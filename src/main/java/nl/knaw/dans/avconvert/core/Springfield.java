package nl.knaw.dans.avconvert.core;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.text.MessageFormat.format;
import static org.apache.commons.io.file.PathUtils.deleteDirectory;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

@Slf4j
public class Springfield {
    public static Map<String, Path> findSpringfieldFiles(Path mappingCsv, Path springfieldDir, String bagParent) throws IOException {
        Map<String, Path> records = new HashMap<>();
        try (Reader reader = Files.newBufferedReader(mappingCsv);
            CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withHeader())) {

            for (CSVRecord csvRecord : csvParser) {
                var pathInSpringfieldDir = csvRecord.get("path_in_springfield_dir");
                var pathInAvDir = csvRecord.get("path_in_AV_dir");
                if (isNotEmpty(pathInSpringfieldDir) && pathInAvDir.startsWith(bagParent)) {
                    var path = springfieldDir.resolve(pathInSpringfieldDir);
                    if (path.toFile().exists())
                        records.put(csvRecord.get("easy_file_id"), path);
                    else {
                        var message = format("File does not exist in Springfield directory: {0} -- {1}", bagParent, pathInSpringfieldDir);
                        log.error(message);
                        throw new IOException(message);
                    }
                }
            }
        }
        return records;
    }

    public static void addSpringfieldFiles(Path bagDir, Map<String, Path> fileIdToPathInSpringfield, Document filesXml) throws IOException {

        var matchingFiles = new HashMap<String, Element>();
        List<Node> newFileList = new ArrayList<>();

        var ids = fileIdToPathInSpringfield.keySet().stream().toList();
        var oldFileList = filesXml.getElementsByTagName("file");
        for (int i = 0; i < oldFileList.getLength(); i++) {
            Element file = (Element) oldFileList.item(i);
            var elements = file.getElementsByTagName("dct:identifier");
            if (elements.getLength() != 0) {
                Element identifier = (Element) elements.item(0);
                if (ids.contains(identifier.getTextContent())) {
                    matchingFiles.put(identifier.getTextContent(), file);
                }
            }
        }
        if (!matchingFiles.keySet().containsAll(ids)) {
            log.error("Not all files found in files.xml: {} {}", ids, matchingFiles.keySet());
            // TODO better move this check to findSpringfieldFiles then the directory is not created
            deleteDirectory(bagDir);
            throw new RuntimeException("Not all files found in files.xml");
        }

        fileIdToPathInSpringfield.keySet().forEach(id -> {
            var oldElement = (Element) matchingFiles.get(id); // TODO not found?
            var newElement = filesXml.createElement("file");
            var newFile = replaceExtension(
                oldElement.getAttribute("filepath"),
                getExtension(fileIdToPathInSpringfield.get(id))
            );
            newElement.setAttribute("filepath", newFile);
            newElement.appendChild(oldElement.getElementsByTagName("accessibleToRights").item(0).cloneNode(true));
            newElement.appendChild(oldElement.getElementsByTagName("visibleToRights").item(0).cloneNode(true));
            newFileList.add(newElement);
            try {
                Files.copy(fileIdToPathInSpringfield.get(id), bagDir.resolve(newFile));
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        // Add the new list of files to the <files> element
        for (Node newFile : newFileList) {
            filesXml.getElementsByTagName("files").item(0).appendChild(newFile);
        }
    }

    private static String replaceExtension(String oldPath, String extension) {
        // TODO Ensure the dot is not at the beginning or end of the filename
        int dotIndex = oldPath.lastIndexOf('.');
        return oldPath.substring(0, dotIndex) + extension;
    }

    private static String getExtension(Path path) {
        // TODO Ensure the dot is not at the beginning or end of the filename
        var fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return fileName.substring(dotIndex + 1);
    }
}
