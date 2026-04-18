package backend.academy.linktracker.bot.kafka;

import backend.academy.linktracker.bot.client.dto.LinkUpdate;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.avro.generic.GenericRecord;

/**
 * Маппер Avro-пейлоуда уведомления в DTO LinkUpdate.
 */
final class LinkUpdatePayloadMapper {

    /**
     * Имя поля идентификатора ссылки в Avro-сообщении.
     */
    private static final String ID_FIELD = "id";
    /**
     * Имя поля URL в Avro-сообщении.
     */
    private static final String URL_FIELD = "url";
    /**
     * Имя поля описания обновления в Avro-сообщении.
     */
    private static final String DESCRIPTION_FIELD = "description";
    /**
     * Имя поля списка chatId в Avro-сообщении.
     */
    private static final String CHAT_IDS_FIELD = "tgChatIds";
    /**
     * Допустимые схемы URL для уведомлений.
     */
    private static final Set<String> SUPPORTED_URL_SCHEMES = Set.of("http", "https");
    /**
     * Максимальная длина URL.
     */
    private static final int URL_MAX_LENGTH = 2048;
    /**
     * Максимальная длина текстового описания обновления.
     */
    private static final int DESCRIPTION_MAX_LENGTH = 4000;

    private LinkUpdatePayloadMapper() {}

    /**
     * Преобразует Avro-событие в DTO для дальнейшей бизнес-обработки.
     *
     * @param event входящее Avro-событие
     * @return DTO обновления ссылки или {@code null}, если событие отсутствует
     */
    static LinkUpdate toLinkUpdate(GenericRecord event) {
        if (event == null) {
            return null;
        }

        Object idValue = event.get(ID_FIELD);
        Object urlValue = event.get(URL_FIELD);
        Object descriptionValue = event.get(DESCRIPTION_FIELD);
        Object tgChatIdsValue = event.get(CHAT_IDS_FIELD);

        if (!(idValue instanceof Number idNumber)
                || !(tgChatIdsValue instanceof List<?> rawChatIds)) {
            throw new InvalidLinkUpdateException("Invalid Avro payload structure");
        }

        String url = requireNonBlankString(urlValue, URL_FIELD);
        String description = requireNonBlankString(descriptionValue, DESCRIPTION_FIELD);
        validateUrl(url);
        validateLength(url, URL_MAX_LENGTH, URL_FIELD);
        validateLength(description, DESCRIPTION_MAX_LENGTH, DESCRIPTION_FIELD);

        List<Long> chatIds = new ArrayList<>(rawChatIds.size());
        for (Object rawChatId : rawChatIds) {
            if (!(rawChatId instanceof Number chatIdNumber)) {
                throw new InvalidLinkUpdateException("Invalid " + CHAT_IDS_FIELD + " value type");
            }
            chatIds.add(chatIdNumber.longValue());
        }

        return new LinkUpdate(idNumber.longValue(), url, description, chatIds);
    }

    /**
     * Возвращает непустую строку поля или выбрасывает ошибку валидации.
     *
     * @param rawValue исходное значение поля
     * @param fieldName имя поля
     * @return очищенное строковое значение
     */
    private static String requireNonBlankString(Object rawValue, String fieldName) {
        if (rawValue == null) {
            throw new InvalidLinkUpdateException("Missing required field: " + fieldName);
        }
        String value = rawValue.toString().trim();
        if (value.isBlank()) {
            throw new InvalidLinkUpdateException("Blank value for field: " + fieldName);
        }
        return value;
    }

    /**
     * Проверяет ограничение длины поля.
     *
     * @param value значение поля
     * @param maxLength максимально допустимая длина
     * @param fieldName имя поля
     */
    private static void validateLength(String value, int maxLength, String fieldName) {
        if (value.length() > maxLength) {
            throw new InvalidLinkUpdateException("Field exceeds max length: " + fieldName);
        }
    }

    /**
     * Валидирует URL по синтаксису, схеме и обязательному host.
     *
     * @param url URL из входящего сообщения
     */
    private static void validateUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException exception) {
            throw new InvalidLinkUpdateException("Invalid URL format");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !SUPPORTED_URL_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new InvalidLinkUpdateException("Unsupported URL scheme");
        }

        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidLinkUpdateException("URL host must be specified");
        }
    }
}
