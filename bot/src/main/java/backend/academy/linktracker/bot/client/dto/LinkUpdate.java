package backend.academy.linktracker.bot.client.dto;

import java.util.List;

public record LinkUpdate(long id, String url, String description, List<Long> tgChatIds) {}
