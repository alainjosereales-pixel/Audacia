package com.audacia.orion.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class XiaoDiscoveryTest {

    @Test
    fun parseBeaconValid() {
        val msg = "ORION_XIAO_CAM:192.168.43.50:81"
        val endpoint = parseBeacon(msg)
        assertNotNull(endpoint)
        assertEquals("192.168.43.50", endpoint?.ip)
        assertEquals(81, endpoint?.streamPort)
    }

    @Test
    fun parseBeaconInvalidPrefix() {
        val msg = "OTHER_CAM:192.168.43.50:81"
        assertNull(parseBeacon(msg))
    }

    @Test
    fun parseBeaconInvalidIp() {
        assertNull(parseBeacon("ORION_XIAO_CAM:999.999.1.1:81"))
        assertNull(parseBeacon("ORION_XIAO_CAM:abc.def.ghi.jkl:81"))
        assertNull(parseBeacon("ORION_XIAO_CAM:192.168.1:81"))
    }

    @Test
    fun parseBeaconInvalidPort() {
        assertNull(parseBeacon("ORION_XIAO_CAM:192.168.43.50:0"))
        assertNull(parseBeacon("ORION_XIAO_CAM:192.168.43.50:999999"))
        assertNull(parseBeacon("ORION_XIAO_CAM:192.168.43.50:abc"))
    }
}
