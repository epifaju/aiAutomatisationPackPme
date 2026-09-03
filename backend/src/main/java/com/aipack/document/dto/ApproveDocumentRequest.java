package com.aipack.document.dto;

import java.util.Map;

public record ApproveDocumentRequest(Map<String, Object> extractedJson, String documentType) {}
