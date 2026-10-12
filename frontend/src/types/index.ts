export interface StationboardEntry {
  number: string;
  category: string;
  to: string;
  stop: {
    departure: string;
    prognosis: {
      departure: string | null;
    };
  };
}

export interface StationboardData {
  stationboard: StationboardEntry[];
}

export interface LightGroup {
  name: string;
  action: {
    on: boolean;
    brightness: number;
  };
}

export type LightGroups = Record<string, LightGroup>;

export interface CalendarData {
  wasteTypes: Record<string, string[]>;
  mrgreen: string[];
}

export interface WifiData {
  ssid: string;
  pass: string;
  qrCodeDataUrl: string;
}

export interface WeatherCurrent {
  temperature: number;
  apparentTemperature: number;
  precipitation: number;
  weatherCode: number;
  windspeed: number;
  relativeHumidity: number;
  isDay: number;
}

export interface WeatherHourly {
  time: string;
  temperature: number;
  apparentTemperature: number;
  precipitationProbability: number;
  weatherCode: number;
}

export interface WeatherDaily {
  date: string;
  weatherCode: number;
  temperatureMax: number;
  temperatureMin: number;
  precipitationSum: number;
  precipitationProbabilityMax: number;
}

export interface WeatherData {
  current: WeatherCurrent;
  hourly: WeatherHourly[];
  daily: WeatherDaily[];
}

export interface SettingsData {
  station: string;
  zip: string;
  latitude: string;
  longitude: string;
  weatherLocation: string;
  calendarRegion: string;
  calendarArea: string;
  calendarTypes: string[];
  calendarLimit: string;
  calendarMrgreenEnabled: boolean;
  calendarMrgreenZip: string;
  calendarMrgreenType: string;
  stationboardEnabled: boolean;
  weatherEnabled: boolean;
  calendarEnabled: boolean;
  wifiEnabled: boolean;
  lightsEnabled: boolean;
}
