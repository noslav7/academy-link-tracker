package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.client.LinkAlreadyTrackedException;
import backend.academy.linktracker.bot.client.ScrapperClient;
import backend.academy.linktracker.bot.client.ScrapperClientException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Отвечает за отправку данных диалога {@code /track} в scrapper.
 */
@Service
public class TrackDialogSubmissionService {

    /**
     * Клиент для вызова API scrapper.
     */
    private final ScrapperClient scrapperClient;

    /**
     * Создает сервис отправки данных диалога.
     *
     * @param scrapperClient клиент взаимодействия со scrapper
     */
    public TrackDialogSubmissionService(ScrapperClient scrapperClient) {
        this.scrapperClient = scrapperClient;
    }

    /**
     * Отправляет ссылку, теги и фильтры в scrapper.
     *
     * @param chatId идентификатор чата
     * @param link ссылка для отслеживания
     * @param tags список тегов
     * @param filters список фильтров
     * @return результат отправки для дальнейшего отображения пользователю
     */
    public TrackSubmissionResult submit(long chatId, String link, List<String> tags, List<String> filters) {
        try {
            scrapperClient.addLink(chatId, link, tags, filters);
            return TrackSubmissionResult.SUCCESS;
        } catch (LinkAlreadyTrackedException ex) {
            return TrackSubmissionResult.DUPLICATE;
        } catch (ScrapperClientException ex) {
            return TrackSubmissionResult.FAILED;
        }
    }
}
