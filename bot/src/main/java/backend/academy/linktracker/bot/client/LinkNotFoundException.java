package backend.academy.linktracker.bot.client;

public class LinkNotFoundException extends ScrapperClientException {
    public LinkNotFoundException() {
        super("Link not found");
    }
}
