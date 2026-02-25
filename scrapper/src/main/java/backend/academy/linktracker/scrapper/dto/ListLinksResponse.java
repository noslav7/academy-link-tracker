package backend.academy.linktracker.scrapper.dto;

import java.util.List;

public record ListLinksResponse(List<LinkResponse> links, int size) {}
