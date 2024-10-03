package com.academy.catalog.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;

@Service
@Slf4j
public class FileService {

    public static void deleteDirectoryRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            // Рекурсивно проходим по всем файлам и подкаталогам и удаляем их
            Files.walk(directory)
                    .sorted(Comparator.reverseOrder()) // Сортируем, чтобы удалять сначала файлы, а потом директории
                    .map(Path::toFile)
                    .forEach(File::delete);

            log.info("Директория '{}' успешно удалена.", directory.toString());
        } else {
            log.warn("Директория '{}' не найдена, возможно она уже удалена.", directory.toString());
        }
    }

    // Метод для вычисления контрольной суммы файла (например, SHA-256)
    public static String calculateFileChecksum(Path filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        // Чтение файла как массива байт
        byte[] fileBytes = Files.readAllBytes(filePath);

        // Вычисляем контрольную сумму (хэш) файла
        byte[] fileChecksum = digest.digest(fileBytes);

        // Преобразуем байты контрольной суммы в шестнадцатеричную строку
        return bytesToHex(fileChecksum);
    }

    // Вспомогательный метод для конвертации байт в строку Hex
    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }


}
