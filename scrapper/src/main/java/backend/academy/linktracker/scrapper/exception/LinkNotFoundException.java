package backend.academy.linktracker.scrapper.exception;

public class LinkNotFoundException extends RuntimeException {
    public LinkNotFoundException(String link) {
        super("Link not found: " + link);
    }
}
