package com.kubetetris.api.journal;

import com.kubetetris.executor.InMemoryJournal;
import com.kubetetris.executor.Journal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Picks the {@link Journal} implementation based on {@code kubetetris.journal.storage}:
 * {@code memory} (default) or {@code file}. The file-backed journal writes JSON per
 * entry under {@code kubetetris.journal.path}; the directory is created on startup if
 * it doesn't exist.
 */
@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(prefix = "kubetetris.journal", name = "storage",
            havingValue = "memory", matchIfMissing = true)
    public Journal inMemoryJournal() {
        return new InMemoryJournal();
    }

    @Bean
    @ConditionalOnProperty(prefix = "kubetetris.journal", name = "storage", havingValue = "file")
    public Journal fileJournal(@Value("${kubetetris.journal.path:./data/journal}") String path) {
        return new FileJournal(Path.of(path));
    }
}
