package pl.fp.wydatki.io;

import pl.fp.wydatki.model.CategorySummary;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.stream.Collectors;

/**
 * FP: zapis wyników – jedyne legalne miejsce efektów ubocznych (I/O).
 * Konwersja danych na String przed zapisem to czysta transformacja.
 */
public final class ResultExporter {
    private ResultExporter() {}

    public static void writeSummaryCsv(Writer writer, List<CategorySummary> summaries)
            throws IOException {
        String content = "category,total,count,average\n" +
                summaries.stream()
                        .map(s -> s.category() + "," + s.total() + ","
                                  + s.count() + "," + s.average())
                        .collect(Collectors.joining("\n"));
        writer.write(content);
    }

    public static void writeHtml(Writer writer, String html) throws IOException {
        writer.write(html);
    }
}
