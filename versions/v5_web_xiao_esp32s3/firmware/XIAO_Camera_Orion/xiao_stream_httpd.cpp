// ============================================================================
// xiao_stream_httpd.cpp - Servidor de Streaming de Alta Velocidad y Control
// Optimizado para Seeed Studio XIAO ESP32S3 Sense (30 FPS Sostenidos)
// ============================================================================

#include "esp_http_server.h"
#include "esp_timer.h"
#include "esp_camera.h"
#include "img_converters.h"
#include "Arduino.h"
#include <lwip/sockets.h>
#include "camera_pins.h"
#include "xiao_stream_httpd.h"

#define PART_BOUNDARY "123456789000000000000987654321"
static const char* _STREAM_CONTENT_TYPE = "multipart/x-mixed-replace;boundary=" PART_BOUNDARY;

httpd_handle_t stream_httpd = NULL;
httpd_handle_t control_httpd = NULL;

#if __has_include("esp_arduino_version.h")
#include "esp_arduino_version.h"
#endif

// ----------------------------------------------------------------------------
// Buzzer PWM (LEDC) - Compatible con ESP32 Arduino Core 2.x y 3.x
// ----------------------------------------------------------------------------
#define BUZZER_CHANNEL 1
#define BUZZER_FREQ 2000
#define BUZZER_RES 8

#if defined(ESP_ARDUINO_VERSION_MAJOR) && (ESP_ARDUINO_VERSION_MAJOR >= 3)
// API para ESP32 Arduino Core 3.0+
void setupBuzzer() {
#if defined(BUZZER_PIN) && (BUZZER_PIN >= 0)
    ledcAttach(BUZZER_PIN, BUZZER_FREQ, BUZZER_RES);
    ledcWrite(BUZZER_PIN, 0);
#endif
}

void setBuzzerState(int on, int duty) {
#if defined(BUZZER_PIN) && (BUZZER_PIN >= 0)
    if (on) {
        ledcWrite(BUZZER_PIN, duty > 0 ? duty : 200);
    } else {
        ledcWrite(BUZZER_PIN, 0);
    }
#endif
}

void beepBuzzer(int duty, int duration_ms, int freq) {
#if defined(BUZZER_PIN) && (BUZZER_PIN >= 0)
    if (freq > 0) {
        ledcAttach(BUZZER_PIN, freq, BUZZER_RES);
    }
    ledcWrite(BUZZER_PIN, duty > 0 ? duty : 200);
    delay(duration_ms > 0 ? duration_ms : 100);
    ledcWrite(BUZZER_PIN, 0);
#endif
}

#else
// API para ESP32 Arduino Core 2.x (Legacy)
void setupBuzzer() {
#if defined(BUZZER_PIN) && (BUZZER_PIN >= 0)
    ledcSetup(BUZZER_CHANNEL, BUZZER_FREQ, BUZZER_RES);
    ledcAttachPin(BUZZER_PIN, BUZZER_CHANNEL);
    ledcWrite(BUZZER_CHANNEL, 0);
#endif
}

void setBuzzerState(int on, int duty) {
#if defined(BUZZER_PIN) && (BUZZER_PIN >= 0)
    if (on) {
        ledcWrite(BUZZER_CHANNEL, duty > 0 ? duty : 200);
    } else {
        ledcWrite(BUZZER_CHANNEL, 0);
    }
#endif
}

void beepBuzzer(int duty, int duration_ms, int freq) {
#if defined(BUZZER_PIN) && (BUZZER_PIN >= 0)
    if (freq > 0) {
        ledcSetup(BUZZER_CHANNEL, freq, BUZZER_RES);
    }
    ledcWrite(BUZZER_CHANNEL, duty > 0 ? duty : 200);
    delay(duration_ms > 0 ? duration_ms : 100);
    ledcWrite(BUZZER_CHANNEL, 0);
#endif
}
#endif

// ----------------------------------------------------------------------------
// Handler: /stream (Puerto 81 - MJPEG de Alta Velocidad con Desacople DMA)
// ----------------------------------------------------------------------------
static volatile bool s_client_streaming = false;
static const size_t STREAM_TX_BUF_SIZE = 65536; // 64 KB en PSRAM para empaquetado contiguo
static const int64_t TARGET_FRAME_INTERVAL_US = 45000; // ~22 FPS sostenidos: óptimo para Wi-Fi 2.4 GHz institucional
static uint8_t *s_tx_buf = NULL;

bool isClientStreaming() {
    return s_client_streaming;
}

// Envía un bloque completo por el socket HTTP, pero si el buffer TCP de LwIP está lleno
// al inicio del frame (sent == 0 tras timeout de SO_SNDTIMEO = 250 ms),
// descarta limpiamente el frame ANTES de comenzar a enviarlo para evitar fragmentos corruptos.
static esp_err_t send_frame_or_drop(httpd_req_t *req, const char *buf, size_t len, int *consecutive_drops) {
    size_t sent = 0;
    int partial_retries = 0;

    while (sent < len) {
        int ret = httpd_send(req, buf + sent, len - sent);
        if (ret > 0) {
            sent += (size_t)ret;
            partial_retries = 0;
            if (consecutive_drops) *consecutive_drops = 0;
        } else if (ret == HTTPD_SOCK_ERR_TIMEOUT) {
            if (sent == 0) {
                // No se ha enviado ni un solo byte de este frame y la cola TCP sigue ocupada (>250 ms):
                // descartar el fotograma completo para no enviar datos rotos ni acumular latencia.
                if (consecutive_drops) {
                    (*consecutive_drops)++;
                    if (*consecutive_drops > 16) {
                        // Más de ~4.0s continuos sin poder transmitir: desconexión real de cliente
                        return ESP_FAIL;
                    }
                }
                return ESP_OK;
            }
            // Si ya se envió parte del frame (sent > 0), completar los bytes restantes
            // para mantener intacta la frontera multipart/JPEG en el stream TCP sin corromper el parser.
            if (++partial_retries > 10) {
                return ESP_FAIL;
            }
            vTaskDelay(pdMS_TO_TICKS(10));
        } else {
            return ESP_FAIL;
        }
    }
    return ESP_OK;
}

static esp_err_t stream_handler(httpd_req_t *req) {
    camera_fb_t *fb = NULL;
    esp_err_t res = ESP_OK;
    char part_buf[128];
    int consecutive_drops = 0;

    // Reservar buffer de transmisión contiguo en PSRAM (una sola vez)
    if (s_tx_buf == NULL) {
        if (psramFound()) {
            s_tx_buf = (uint8_t *)ps_malloc(STREAM_TX_BUF_SIZE);
        }
        if (s_tx_buf == NULL) {
            s_tx_buf = (uint8_t *)malloc(STREAM_TX_BUF_SIZE);
        }
    }

    // Optimización de socket TCP: desactivar Nagle (TCP_NODELAY) en el emisor y fijar
    // un timeout de envío calibrado a 250 ms (absorbe fluctuaciones normales de Wi-Fi institucional)
    int sockfd = httpd_req_to_sockfd(req);
    if (sockfd >= 0) {
        int nodelay = 1;
        setsockopt(sockfd, IPPROTO_TCP, TCP_NODELAY, (char *)&nodelay, sizeof(nodelay));

        struct timeval snd_tv;
        snd_tv.tv_sec = 0;
        snd_tv.tv_usec = 250000; // 250 ms de margen para confirmar entrega en red con APs
        setsockopt(sockfd, SOL_SOCKET, SO_SNDTIMEO, &snd_tv, sizeof(snd_tv));
    }

    // Enviar cabecera HTTP 200 OK directa (sin Transfer-Encoding: chunked) para evitar
    // los micro-paquetes de 2 y 5 bytes que saturan los 16 slots de TCP_SND_QUEUELEN en LwIP
    static const char *STREAM_HTTP_HEADER =
        "HTTP/1.1 200 OK\r\n"
        "Content-Type: multipart/x-mixed-replace;boundary=" PART_BOUNDARY "\r\n"
        "Access-Control-Allow-Origin: *\r\n"
        "Cache-Control: no-store, no-cache, must-revalidate, max-age=0\r\n"
        "Connection: close\r\n"
        "X-Framerate: 22\r\n\r\n";

    res = send_frame_or_drop(req, STREAM_HTTP_HEADER, strlen(STREAM_HTTP_HEADER), &consecutive_drops);
    if (res != ESP_OK) return res;

    s_client_streaming = true;

    while (true) {
        int64_t frame_start_us = esp_timer_get_time();

        // 1. Obtener fotograma del buffer DMA (sincronizado por hardware VSYNC del OV2640 a ~30 FPS)
        fb = esp_camera_fb_get();
        if (!fb) {
            res = ESP_FAIL;
            break;
        }

        if (fb->format == PIXFORMAT_JPEG) {
            size_t jpg_len = fb->len;
            size_t hlen = snprintf(part_buf, sizeof(part_buf), 
                                   "\r\n--" PART_BOUNDARY "\r\nContent-Type: image/jpeg\r\nContent-Length: %u\r\n\r\n", 
                                   (unsigned int)jpg_len);

            if (s_tx_buf != NULL && (hlen + jpg_len) <= STREAM_TX_BUF_SIZE) {
                // 2. DESACOPLE INMEDIATO DE DMA: Copiar cabecera + JPEG a memoria contigua (~15 us)
                // y liberar el buffer DMA de la cámara ANTES de transmitir por Wi-Fi.
                memcpy(s_tx_buf, part_buf, hlen);
                memcpy(s_tx_buf + hlen, fb->buf, jpg_len);
                esp_camera_fb_return(fb);
                fb = NULL;

                // 3. Envío directo en UN SOLO bloque contiguo con descarte si la cola TCP no acepta a tiempo
                res = send_frame_or_drop(req, (const char *)s_tx_buf, hlen + jpg_len, &consecutive_drops);
            } else {
                esp_camera_fb_return(fb);
                fb = NULL;
            }
        } else {
            esp_camera_fb_return(fb);
            fb = NULL;
        }

        if (res != ESP_OK) {
            // El cliente cerró la pestaña del navegador o desconectó el socket
            break;
        }

        // 4. Frame Pacing: regula la salida a ~22 FPS máx (45 ms/frame) y garantiza
        // al menos 5-8 ms libres para que la tarea Wi-Fi/LwIP procese ACKs sin llenar la cola TCP
        int64_t elapsed_us = esp_timer_get_time() - frame_start_us;
        int64_t remaining_us = TARGET_FRAME_INTERVAL_US - elapsed_us;
        uint32_t delay_ms = (remaining_us > 5000) ? (uint32_t)(remaining_us / 1000) : 5;
        vTaskDelay(pdMS_TO_TICKS(delay_ms));
    }

    s_client_streaming = false;
    return res;
}

// ----------------------------------------------------------------------------
// Handler: /status (Puerto 80 - JSON de Telemetría)
// ----------------------------------------------------------------------------
static esp_err_t status_handler(httpd_req_t *req) {
    static char json_response[256];
    sensor_t *s = esp_camera_sensor_get();
    if (!s) {
        httpd_resp_send_500(req);
        return ESP_FAIL;
    }

    char *p = json_response;
    *p++ = '{';
    p += sprintf(p, "\"device\":\"XIAO_ESP32S3\",");
    p += sprintf(p, "\"framesize\":%u,", s->status.framesize);
    p += sprintf(p, "\"quality\":%u,", s->status.quality);
    p += sprintf(p, "\"brightness\":%d,", s->status.brightness);
    p += sprintf(p, "\"contrast\":%d,", s->status.contrast);
    p += sprintf(p, "\"saturation\":%d", s->status.saturation);
    *p++ = '}';
    *p = 0;

    httpd_resp_set_type(req, "application/json");
    httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
    return httpd_resp_send(req, json_response, strlen(json_response));
}

// ----------------------------------------------------------------------------
// Handler: /control (Puerto 80 - Ajuste Dinámico de Parámetros)
// ----------------------------------------------------------------------------
static esp_err_t control_handler(httpd_req_t *req) {
    char *buf = NULL;
    size_t buf_len = httpd_req_get_url_query_len(req) + 1;
    if (buf_len > 1) {
        buf = (char *)malloc(buf_len);
        if (!buf) {
            httpd_resp_send_500(req);
            return ESP_FAIL;
        }
        if (httpd_req_get_url_query_str(req, buf, buf_len) == ESP_OK) {
            char var[32] = {0};
            char val[32] = {0};
            if (httpd_query_key_value(buf, "var", var, sizeof(var)) == ESP_OK &&
                httpd_query_key_value(buf, "val", val, sizeof(val)) == ESP_OK) {
                sensor_t *s = esp_camera_sensor_get();
                int int_val = atoi(val);
                if (s) {
                    if (!strcmp(var, "framesize")) s->set_framesize(s, (framesize_t)int_val);
                    else if (!strcmp(var, "quality")) s->set_quality(s, int_val);
                    else if (!strcmp(var, "contrast")) s->set_contrast(s, int_val);
                    else if (!strcmp(var, "brightness")) s->set_brightness(s, int_val);
                    else if (!strcmp(var, "saturation")) s->set_saturation(s, int_val);
                }
            }
        }
        free(buf);
    }
    httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
    return httpd_resp_send(req, "OK", 2);
}

// ----------------------------------------------------------------------------
// Handler: /buzzer (Puerto 80 - Control de Proximidad)
// ----------------------------------------------------------------------------
static esp_err_t buzzer_handler(httpd_req_t *req) {
    char *buf = NULL;
    size_t buf_len = httpd_req_get_url_query_len(req) + 1;
    if (buf_len > 1) {
        buf = (char *)malloc(buf_len);
        if (buf && httpd_req_get_url_query_str(req, buf, buf_len) == ESP_OK) {
            char on_val[8] = {0};
            char duty_val[8] = {0};
            char beep_val[8] = {0};
            char freq_val[8] = {0};

            int duty = 200;
            if (httpd_query_key_value(buf, "duty", duty_val, sizeof(duty_val)) == ESP_OK) {
                duty = atoi(duty_val);
            }

            if (httpd_query_key_value(buf, "beep", beep_val, sizeof(beep_val)) == ESP_OK) {
                int dur = atoi(beep_val);
                int freq = 2000;
                if (httpd_query_key_value(buf, "freq", freq_val, sizeof(freq_val)) == ESP_OK) {
                    freq = atoi(freq_val);
                }
                beepBuzzer(duty, dur, freq);
            } else if (httpd_query_key_value(buf, "on", on_val, sizeof(on_val)) == ESP_OK) {
                setBuzzerState(atoi(on_val), duty);
            }
        }
        if (buf) free(buf);
    }
    httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
    return httpd_resp_send(req, "OK", 2);
}

// ----------------------------------------------------------------------------
// Handler: /whoami y / (Identificación Inmediata)
// ----------------------------------------------------------------------------
static esp_err_t whoami_handler(httpd_req_t *req) {
    const char *resp = "{\"device\":\"XIAO_ESP32S3_ORION\",\"status\":\"online\",\"stream_port\":81}";
    httpd_resp_set_type(req, "application/json");
    httpd_resp_set_hdr(req, "Access-Control-Allow-Origin", "*");
    return httpd_resp_send(req, resp, strlen(resp));
}

// ----------------------------------------------------------------------------
// Inicialización de Servidores HTTP (Puertos 80 y 81)
// ----------------------------------------------------------------------------
void startCameraServer() {
    setupBuzzer();

    httpd_config_t config = HTTPD_DEFAULT_CONFIG();
    config.max_uri_handlers = 8;
    config.stack_size = 8192;

    // --- Protección contra desconexiones y conexiones colgadas ---
    config.lru_purge_enable = true;   // Purga automáticamente sockets inactivos ante cierres abruptos
    config.recv_wait_timeout = 5;     // 5 segundos de margen para tolerar fluctuaciones normales de Wi-Fi
    config.send_wait_timeout = 5;     // Evita desconexiones prematuras que maten el stream
    config.backlog_conn = 4;          // Aceptación fluida sin rechazo de conexiones

    // 1. Servidor de Control en Puerto 80
    httpd_uri_t status_uri = {
        .uri       = "/status",
        .method    = HTTP_GET,
        .handler   = status_handler,
        .user_ctx  = NULL
    };
    httpd_uri_t control_uri = {
        .uri       = "/control",
        .method    = HTTP_GET,
        .handler   = control_handler,
        .user_ctx  = NULL
    };
    httpd_uri_t buzzer_uri = {
        .uri       = "/buzzer",
        .method    = HTTP_GET,
        .handler   = buzzer_handler,
        .user_ctx  = NULL
    };
    httpd_uri_t whoami_uri = {
        .uri       = "/whoami",
        .method    = HTTP_GET,
        .handler   = whoami_handler,
        .user_ctx  = NULL
    };
    httpd_uri_t root_uri = {
        .uri       = "/",
        .method    = HTTP_GET,
        .handler   = whoami_handler,
        .user_ctx  = NULL
    };

    config.server_port = 80;
    config.ctrl_port = 32768;
    if (httpd_start(&control_httpd, &config) == ESP_OK) {
        httpd_register_uri_handler(control_httpd, &status_uri);
        httpd_register_uri_handler(control_httpd, &control_uri);
        httpd_register_uri_handler(control_httpd, &buzzer_uri);
        httpd_register_uri_handler(control_httpd, &whoami_uri);
        httpd_register_uri_handler(control_httpd, &root_uri);
        Serial.println("  [HTTP] Servidor de control iniciado en puerto 80.");
    }

    // 2. Servidor de Streaming en Puerto 81
    httpd_uri_t stream_uri = {
        .uri       = "/stream",
        .method    = HTTP_GET,
        .handler   = stream_handler,
        .user_ctx  = NULL
    };

    config.server_port = 81;
    config.ctrl_port = 32769;
    config.core_id = 1;               // CRÍTICO: Núcleo 1 exclusivo para streaming, liberando Núcleo 0 para Wi-Fi
    config.max_open_sockets = 2;      // Purga inmediata de socket viejo al recargar la página con F5
    config.lru_purge_enable = true;   // Desconecta sockets inactivos sin dar error 'accept (128)'
    if (httpd_start(&stream_httpd, &config) == ESP_OK) {
        httpd_register_uri_handler(stream_httpd, &stream_uri);
        Serial.println("  [HTTP] Servidor de video stream iniciado en puerto 81 (Core 1).");
    }
}
