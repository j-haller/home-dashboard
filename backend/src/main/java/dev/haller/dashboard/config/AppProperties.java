package dev.haller.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dashboard")
public class AppProperties {

    private Stationboard stationboard = new Stationboard();
    private Lights lights = new Lights();
    private Calendar calendar = new Calendar();
    private Wifi wifi = new Wifi();
    private Weather weather = new Weather();

    public Stationboard getStationboard() { return stationboard; }
    public void setStationboard(Stationboard stationboard) { this.stationboard = stationboard; }

    public Lights getLights() { return lights; }
    public void setLights(Lights lights) { this.lights = lights; }

    public Calendar getCalendar() { return calendar; }
    public void setCalendar(Calendar calendar) { this.calendar = calendar; }

    public Wifi getWifi() { return wifi; }
    public void setWifi(Wifi wifi) { this.wifi = wifi; }

    public Weather getWeather() { return weather; }
    public void setWeather(Weather weather) { this.weather = weather; }

    public static class Stationboard {
        private String station = "Zurich HB";
        private int limit = 10;
        private int showDelays = 1;
        private String apiUrl = "https://search.ch/timetable/api/stationboard.json";

        public String getStation() { return station; }
        public void setStation(String station) { this.station = station; }
        public int getLimit() { return limit; }
        public int getShowDelays() { return showDelays; }
        public void setLimit(int limit) { this.limit = limit; }
        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    }

    public static class Lights {
        private String apiKey = "";
        private String bridgeAddressFallback = "";
        private String discoveryApiUrl = "https://discovery.meethue.com/";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBridgeAddressFallback() { return bridgeAddressFallback; }
        public void setBridgeAddressFallback(String bridgeAddressFallback) { this.bridgeAddressFallback = bridgeAddressFallback; }
        public String getDiscoveryApiUrl() { return discoveryApiUrl; }
        public void setDiscoveryApiUrl(String discoveryApiUrl) { this.discoveryApiUrl = discoveryApiUrl; }
    }

    public static class Calendar {
        private String zip = "8000";

        public String getZip() { return zip; }
        public void setZip(String zip) { this.zip = zip; }
    }

    public static class Wifi {
        private String ssid = "";
        private String password = "";

        public String getSsid() { return ssid; }
        public void setSsid(String ssid) { this.ssid = ssid; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class Weather {
        private String latitude = "47.37";
        private String longitude = "8.54";
        private String apiUrl = "https://api.open-meteo.com/v1/forecast";

        public String getLatitude() { return latitude; }
        public void setLatitude(String latitude) { this.latitude = latitude; }
        public String getLongitude() { return longitude; }
        public void setLongitude(String longitude) { this.longitude = longitude; }
        public String getApiUrl() { return apiUrl; }
        public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }
    }
}
