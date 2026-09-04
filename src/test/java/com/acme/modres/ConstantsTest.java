package com.acme.modres;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConstantsTest {

    @Test
    void testBarcelona_constantValue() {
        assertEquals("Barcelona", Constants.BARCELONA);
    }

    @Test
    void testCork_constantValue() {
        assertEquals("Cork", Constants.CORK);
    }

    @Test
    void testMiami_constantValue() {
        assertEquals("Miami", Constants.MIAMI);
    }

    @Test
    void testSanFrancisco_constantValue() {
        assertEquals("San_Francisco", Constants.SAN_FRANCISCO);
    }

    @Test
    void testParis_constantValue() {
        assertEquals("Paris", Constants.PARIS);
    }

    @Test
    void testLasVegas_constantValue() {
        assertEquals("Las_Vegas", Constants.LAS_VEGAS);
    }

    @Test
    void testSupportedCities_containsAllSixCities() {
        assertEquals(6, Constants.SUPPORTED_CITIES.length);
    }

    @Test
    void testSupportedCities_containsParis() {
        boolean found = false;
        for (String city : Constants.SUPPORTED_CITIES) {
            if (Constants.PARIS.equals(city)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    void testSupportedCities_containsLasVegas() {
        boolean found = false;
        for (String city : Constants.SUPPORTED_CITIES) {
            if (Constants.LAS_VEGAS.equals(city)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    void testSupportedCities_containsSanFrancisco() {
        boolean found = false;
        for (String city : Constants.SUPPORTED_CITIES) {
            if (Constants.SAN_FRANCISCO.equals(city)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    void testSupportedCities_containsMiami() {
        boolean found = false;
        for (String city : Constants.SUPPORTED_CITIES) {
            if (Constants.MIAMI.equals(city)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    void testSupportedCities_containsCork() {
        boolean found = false;
        for (String city : Constants.SUPPORTED_CITIES) {
            if (Constants.CORK.equals(city)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    void testSupportedCities_containsBarcelona() {
        boolean found = false;
        for (String city : Constants.SUPPORTED_CITIES) {
            if (Constants.BARCELONA.equals(city)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    void testBarcelonaWeatherFile_constantValue() {
        assertEquals("barcelona.json", Constants.BACELONA_WEATHER_FILE);
    }

    @Test
    void testCorkWeatherFile_constantValue() {
        assertEquals("cork.json", Constants.CORK_WEATHER_FILE);
    }

    @Test
    void testLasVegasWeatherFile_constantValue() {
        assertEquals("nv.json", Constants.LAS_VEGAS_WEATHER_FILE);
    }

    @Test
    void testMiamiWeatherFile_constantValue() {
        assertEquals("miami.json", Constants.MIAMI_WEATHER_FILE);
    }

    @Test
    void testParisWeatherFile_constantValue() {
        assertEquals("paris.json", Constants.PARIS_WEATHER_FILE);
    }

    @Test
    void testSanFranciscoWeatherFile_constantValue() {
        assertEquals("sanfran.json", Constants.SAN_FRANCESCO_WEATHER_FILE);
    }

    @Test
    void testWundergroundApiPrefix_constantValue() {
        assertEquals("http://api.wunderground.com/api/", Constants.WUNDERGROUND_API_PREFIX);
    }

    @Test
    void testWundergroundApiPart_constantValue() {
        assertEquals("/forecast/geolookup/conditions/q/", Constants.WUNDERGROUND_API_PART);
    }

    @Test
    void testDataFormat_constantValue() {
        assertEquals("MM/dd/yyyy", Constants.DATA_FORMAT);
    }
}
