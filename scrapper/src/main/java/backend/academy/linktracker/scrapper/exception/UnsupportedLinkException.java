package backend.academy.linktracker.scrapper.exception;

public class UnsupportedLinkException extends RuntimeException {
    public UnsupportedLinkException(String link) {
        super("Unsupported link: " + link);
    }
}
