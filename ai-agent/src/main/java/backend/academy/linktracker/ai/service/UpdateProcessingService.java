package backend.academy.linktracker.ai.service;

import backend.academy.linktracker.ai.dto.ProcessedLinkUpdate;
import backend.academy.linktracker.ai.dto.RawLinkUpdate;
import backend.academy.linktracker.ai.properties.AiAgentProperties;
import jakarta.validation.Valid;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

/**
 * Оркестрирует фильтрацию и суммаризацию входящего обновления.
 */
@Service
@Validated
public class UpdateProcessingService {

    /**
     * Компонент фильтрации, нужен для раннего отбрасывания обновлений до дорогих этапов обработки.
     */
    private final UpdateFilter updateFilter;

    /**
     * Компонент суммаризации, нужен для сокращения длинных описаний перед доставкой.
     */
    private final UpdateSummarizer updateSummarizer;

    /**
     * Фабрика результата, нужна для отделения сборки выходного DTO от логики оркестрации.
     */
    private final ProcessedLinkUpdateFactory processedLinkUpdateFactory;

    /**
     * Настройки модуля, нужны для принятия решений о порогах обработки.
     */
    private final AiAgentProperties aiAgentProperties;

    /**
     * Создает сервис с дефолтной фабрикой результата для package-local сценариев без Spring-контейнера.
     *
     * @param updateFilter фильтр входящих обновлений
     * @param updateSummarizer суммаризатор описаний
     * @param aiAgentProperties настройки AI Agent
     */
    UpdateProcessingService(
            UpdateFilter updateFilter, UpdateSummarizer updateSummarizer, AiAgentProperties aiAgentProperties) {
        this(updateFilter, updateSummarizer, new ProcessedLinkUpdateFactory(), aiAgentProperties);
    }

    /**
     * Создает сервис обработки с явно переданными этапами пайплайна, чтобы Spring собирал production-граф зависимостей.
     *
     * @param updateFilter фильтр входящих обновлений
     * @param updateSummarizer суммаризатор описаний
     * @param processedLinkUpdateFactory фабрика результата обработки
     * @param aiAgentProperties настройки AI Agent
     */
    @Autowired
    public UpdateProcessingService(
            UpdateFilter updateFilter,
            UpdateSummarizer updateSummarizer,
            ProcessedLinkUpdateFactory processedLinkUpdateFactory,
            AiAgentProperties aiAgentProperties) {
        this.updateFilter = updateFilter;
        this.updateSummarizer = updateSummarizer;
        this.processedLinkUpdateFactory = processedLinkUpdateFactory;
        this.aiAgentProperties = aiAgentProperties;
    }

    /**
     * Возвращает обработанное обновление или пустой результат, чтобы следующий этап получил только полезные события.
     *
     * @param update входящее обновление
     * @return результат обработки
     */
    public Optional<ProcessedLinkUpdate> process(@Valid RawLinkUpdate update) {
        Objects.requireNonNull(update, "update");
        if (!updateFilter.shouldProcess(update)) {
            return Optional.empty();
        }

        String description = Objects.toString(update.description(), "");
        String processedDescription =
                shouldSummarize(description) ? updateSummarizer.summarize(description) : description;
        return Optional.of(processedLinkUpdateFactory.create(update, processedDescription));
    }

    /**
     * Проверяет необходимость суммаризации, чтобы короткие описания не проходили лишний этап обработки.
     *
     * @param description нормализованное до непустой ссылки описание обновления
     * @return true, если описание длиннее настроенного порога
     */
    private boolean shouldSummarize(String description) {
        return description.length() > aiAgentProperties.getSummarization().getThreshold();
    }
}
