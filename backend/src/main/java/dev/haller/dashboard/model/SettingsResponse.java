package dev.haller.dashboard.model;

import java.util.List;

public record SettingsResponse(
    String station,
    String zip,
    String latitude,
    String longitude,
    String weatherLocation,
    String calendarRegion,
    String calendarArea,
    List<String> calendarTypes,
    String calendarLimit,
    boolean calendarMrgreenEnabled,
    String calendarMrgreenZip,
    String calendarMrgreenType,
    boolean stationboardEnabled,
    boolean weatherEnabled,
    boolean calendarEnabled,
    boolean wifiEnabled,
    boolean lightsEnabled
) {}
