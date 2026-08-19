package com.ai.openai_api_service.service.timing;

import com.ai.openai_api_service.model.ChatMode;
import com.ai.openai_api_service.model.RequestUnderstandType;

/**
 * Mutable per-turn fields for the compact REQUEST ROUTING log.
 */
public final class RoutingSummaryState {

    private String requestText = "";
    private String mode = "-";
    private String router = "-";
    private String type = "-";
    private String override = "none";
    private String route = "-";
    private String handler = "-";
    private String action = "-";

    public void setRequestText(String requestText) {
        this.requestText = requestText != null ? requestText : "";
    }

    public void setMode(ChatMode chatMode) {
        this.mode = chatMode != null ? chatMode.name() : "-";
    }

    public void setRouter(String router) {
        this.router = blankToDash(router);
    }

    public void setType(RequestUnderstandType understandType) {
        this.type = understandType != null ? understandType.name() : "-";
    }

    public void setTypeRaw(String type) {
        this.type = blankToDash(type);
    }

    public void setOverride(String override) {
        this.override = override == null || override.isBlank() ? "none" : override;
    }

    public void setRoute(String route) {
        this.route = blankToDash(route);
    }

    public void setHandler(String handler) {
        this.handler = blankToDash(handler);
    }

    public void setAction(String action) {
        this.action = blankToDash(action);
    }

    public String getRequestText() {
        return requestText;
    }

    public String getMode() {
        return mode;
    }

    public String getRouter() {
        return router;
    }

    public String getType() {
        return type;
    }

    public String getOverride() {
        return override;
    }

    public String getRoute() {
        return route;
    }

    public String getHandler() {
        return handler;
    }

    public String getAction() {
        return action;
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
