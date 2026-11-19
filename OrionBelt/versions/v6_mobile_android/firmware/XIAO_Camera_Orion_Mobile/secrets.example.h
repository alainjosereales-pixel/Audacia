#ifndef ORION_SECRETS_H
#define ORION_SECRETS_H

// ============================================================================
// ORION v6 Mobile: Plantilla de Credenciales Wi-Fi (secrets.example.h)
// ============================================================================
// INSTRUCCIONES:
// Copia este archivo como "secrets.h" en esta misma carpeta y coloca tus
// contraseñas reales. "secrets.h" está estrictamente ignorado por .gitignore.
// ============================================================================

struct WifiCredential {
    const char* ssid;
    const char* password;
    const char* description;
};

// Lista de redes ordenada por prioridad estricta:
// El índice 0 tiene la máxima prioridad.
const WifiCredential KNOWN_NETWORKS[] = {
    {"MI_CELULAR_HOTSPOT", "mi_password_movil", "Punto de Acceso Móvil (Prioridad 1)"},
    {"CELULAR_RESPALDO",   "password_respaldo",  "Celular de Respaldo (Prioridad 2)"},
    {"Audacia",            "clave_red_audacia",  "Red de Laboratorio AudacIA (Prioridad 3)"}
};

const size_t NUM_KNOWN_NETWORKS = sizeof(KNOWN_NETWORKS) / sizeof(KNOWN_NETWORKS[0]);

// Configuración opcional de IP estática (0 = Desactivado, usa DHCP recomendado)
#define ORION_USE_STATIC_IP 0
#define ORION_STATIC_IP     192, 168, 43, 50
#define ORION_GATEWAY       192, 168, 43, 1
#define ORION_SUBNET        255, 255, 255, 0

#endif // ORION_SECRETS_H
