import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.*;
import java.util.concurrent.*;

public class AudioImageDownloader {

    private static final String PIC_URLS_FILE = "urls_pic.txt";
    private static final String MUSIC_URLS_FILE = "urls_mus.txt";

    private static final String PICTURES_DIR = "pictures/";
    private static final String MUSIC_DIR = "music/";

    private static final int THREAD_POOL_SIZE = 10;

    public static void main(String[] args) {
        try {
            // Создаем директории для загрузок
            Files.createDirectories(Paths.get(PICTURES_DIR));
            Files.createDirectories(Paths.get(MUSIC_DIR));

            System.out.println("=== Начинаем загрузку ===");

            // Загружаем музыку
            System.out.println("\n🎵 Загрузка музыки...");
            downloadFromFile(MUSIC_URLS_FILE, MUSIC_DIR, "audio", ".mp3");

            // Загружаем картинки
            System.out.println("\n🖼️ Загрузка картинок...");
            downloadFromFile(PIC_URLS_FILE, PICTURES_DIR, "image", ".jpg");

            System.out.println("\n✅ Все загрузки завершены!");

        } catch (IOException e) {
            System.err.println("❌ Критическая ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void downloadFromFile(String urlListFile,
                                         String downloadDir,
                                         String filePrefix,
                                         String defaultExtension) {

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        try (BufferedReader reader = new BufferedReader(new FileReader(urlListFile))) {
            String urlLine;
            int fileCount = 0;
            var futures = new java.util.ArrayList<Future<?>>();

            while ((urlLine = reader.readLine()) != null) {
                if (urlLine.trim().isEmpty()) continue;

                final String currentUrl = urlLine.trim();
                final String extension = getFileExtension(currentUrl, defaultExtension);
                final String filePath = downloadDir + filePrefix + "_" + (fileCount + 1) + extension;
                final int currentNumber = fileCount + 1;

                // Запускаем загрузку в отдельном потоке
                Future<?> future = executor.submit(() -> {
                    try {
                        System.out.println("📥 Начинаем загрузку [" + currentNumber + "]: " + currentUrl);
                        downloadFile(currentUrl, filePath);
                        System.out.println("✅ Успешно загружено: " + filePath);
                    } catch (IOException e) {
                        System.err.println("❌ Ошибка загрузки [" + currentNumber + "]: " + e.getMessage());
                    }
                });

                futures.add(future);
                fileCount++;

                // Небольшая пауза между созданием задач
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Ждем завершения всех задач
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    System.err.println("Задача прервана: " + e.getMessage());
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    System.err.println("Ошибка выполнения: " + e.getCause().getMessage());
                }
            }

            System.out.println("📊 Обработано ссылок: " + fileCount);

        } catch (FileNotFoundException e) {
            System.err.println("❌ Файл не найден: " + urlListFile);
        } catch (IOException e) {
            System.err.println("❌ Ошибка чтения файла: " + e.getMessage());
        } finally {
            // Корректное завершение пула потоков
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.err.println("⚠️ Принудительное завершение потоков...");
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void downloadFile(String sourceUrl, String destinationPath) throws IOException {
        URL url = new URL(sourceUrl);
        var connection = (java.net.HttpURLConnection) url.openConnection();

        // Настраиваем соединение
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);

        try (ReadableByteChannel channel = Channels.newChannel(connection.getInputStream());
             FileOutputStream output = new FileOutputStream(destinationPath)) {
            output.getChannel().transferFrom(channel, 0, Long.MAX_VALUE);
        }
    }

    private static String getFileExtension(String url, String defaultExtension) {
        if (url.contains(".")) {
            // Убираем параметры запроса
            String cleanUrl = url.split("\\?")[0];
            String extension = cleanUrl.substring(cleanUrl.lastIndexOf(".")).toLowerCase();

            // Проверяем, что расширение допустимое
            if (extension.matches("\\.(jpg|jpeg|png|gif|bmp|webp|mp3|wav|flac|ogg|aac)")) {
                if (extension.equals(".jpeg")) return ".jpg";
                return extension;
            }
        }
        return defaultExtension;
    }
}