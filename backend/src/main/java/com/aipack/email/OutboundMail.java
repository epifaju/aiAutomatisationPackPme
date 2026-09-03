package com.aipack.email;

public record OutboundMail(String from, String to, String subject, String body) {}
