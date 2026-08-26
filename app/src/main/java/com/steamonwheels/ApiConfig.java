package com.steamonwheels;

/**
 * Single source of truth for the backend's base URL, shared by every
 * service class (TranslationService, AuthService, LessonService) so it
 * only needs updating in one place.
 */
public class ApiConfig {
    public static final String BASE_URL = "https://steamonwheels-production.up.railway.app";
}
