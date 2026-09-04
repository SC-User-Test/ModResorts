package com.acme.modres;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultWeatherDataTest {

    @Test
    void testConstructor_withNullCity_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> new DefaultWeatherData(null));
    }

    @Test
    void testConstructor_withUnsupportedCity_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> new DefaultWeatherData("Tokyo"));
    }

    @Test
    void testConstructor_withParis_createsInstance() {
        DefaultWeatherData data = new DefaultWeatherData(Constants.PARIS);
        assertNotNull(data);
    }

    @Test
    void testConstructor_withLasVegas_createsInstance() {
        DefaultWeatherData data = new DefaultWeatherData(Constants.LAS_VEGAS);
        assertNotNull(data);
    }

    @Test
    void testConstructor_withSanFrancisco_createsInstance() {
        DefaultWeatherData data = new DefaultWeatherData(Constants.SAN_FRANCISCO);
        assertNotNull(data);
    }

    @Test
    void testConstructor_withMiami_createsInstance() {
        DefaultWeatherData data = new DefaultWeatherData(Constants.MIAMI);
        assertNotNull(data);
    }

    @Test
    void testConstructor_withCork_createsInstance() {
        DefaultWeatherData data = new DefaultWeatherData(Constants.CORK);
        assertNotNull(data);
    }

    @Test
    void testConstructor_withBarcelona_createsInstance() {
        DefaultWeatherData data = new DefaultWeatherData(Constants.BARCELONA);
        assertNotNull(data);
    }

    @Test
    void testGetCity_withParis_returnsParis() {
        DefaultWeatherData data = new DefaultWeatherData(Constants.PARIS);
        assertEquals(Constants.PARIS, data.getCity());
    }

    @Test
    void testGetCity_withLasVegas_returnsLasVegas() {
        DefaultWeatherData data = new DefaultWeatherData(Constants.LAS_VEGAS);
        assertEquals(Constants.LAS_VEGAS, data.getCity());
    }

    @Test
    void testGetCity_withMiami_returnsMiami() {
        DefaultWeatherData data = new DefaultWeatherData(Constants.MIAMI);
        assertEquals(Constants.MIAMI, data.getCity());
    }

    @Test
    void testGetCity_withCork_returnsCork() {
        DefaultWeatherData data = new DefaultWeatherData(Constants.CORK);
        assertEquals(Constants.CORK, data.getCity());
    }

    @Test
    void testGetCity_withBarcelona_returnsBarcelona() {
        DefaultWeatherData data = new DefaultWeatherData(Constants.BARCELONA);
        assertEquals(Constants.BARCELONA, data.getCity());
    }

    @Test
    void testGetCity_withSanFrancisco_returnsSanFrancisco() {
        DefaultWeatherData data = new DefaultWeatherData(Constants.SAN_FRANCISCO);
        assertEquals(Constants.SAN_FRANCISCO, data.getCity());
    }

    @Test
    void testConstructor_withEmptyString_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> new DefaultWeatherData(""));
    }

    @Test
    void testConstructor_withCaseSensitiveCity_throwsUnsupportedOperationException() {
        // "paris" (lowercase) is not in supported cities
        assertThrows(UnsupportedOperationException.class, () -> new DefaultWeatherData("paris"));
    }

    @Test
    void testConstructor_withAllSupportedCities_noException() {
        for (String city : Constants.SUPPORTED_CITIES) {
            assertDoesNotThrow(() -> new DefaultWeatherData(city));
        }
    }
}
