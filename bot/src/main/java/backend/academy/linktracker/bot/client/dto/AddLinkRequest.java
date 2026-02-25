package backend.academy.linktracker.bot.client.dto;

import java.util.List;

public record AddLinkRequest(String link, List<String> tags, List<String> filters) {}
