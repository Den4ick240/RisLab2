package ru.nsu.zhigalov.ris;

import lombok.SneakyThrows;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.openstreetmap.osm._0.Node;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Consumer;

import static javax.xml.stream.XMLStreamConstants.END_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

public class JaxbMain {
    @SneakyThrows
    public static void main(String[] args) {
        var inPath = "RU-NVS.osm.bz2";
//        var outPath = "out.xml";
        String outPath = null;
        var length = 2048 * 32;
        var calcStat = true;

        try (var in = new BZip2CompressorInputStream(new BufferedInputStream(new FileInputStream(inPath), 4096 * 32))) {
            if (outPath != null) {
                try (var out = new FileOutputStream(outPath)) {
                    IOUtils.copyRange(in, length, out);
                }
            }

            if (calcStat) {
                Map<String, Map<BigInteger, Integer>> stat = new HashMap<>();
                long start = System.currentTimeMillis();
                var nodeReader = new PartialUnmarshaller<>(in, Node.class);
                for (Node node : nodeReader) {
//                    if (in.getBytesRead() > length) break;
                    var userStat = stat.computeIfAbsent(node.getUser(), k -> new HashMap<>());
                    userStat.merge(node.getChangeset(), 1, Integer::sum);
                }
                long end = System.currentTimeMillis();
                System.out.println(stat.size());
                stat.forEach(
                        (key, val) -> System.out.println(key + val.toString())
                );
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class PartialUnmarshaller<T> implements Iterable<T>, Iterator<T> {
        private final Class<T> clazz;
        private final Unmarshaller unmarshaller;
        private final XMLStreamReader reader;
        boolean endOfNodes = false;

        public PartialUnmarshaller(InputStream stream, Class<T> clazz) throws XMLStreamException, JAXBException {
            this.clazz = clazz;
            this.unmarshaller = JAXBContext.newInstance(clazz).createUnmarshaller();
            this.reader = XMLInputFactory.newInstance().createXMLStreamReader(stream);
        }

        @Override
        public Iterator<T> iterator() {
            return this;
        }

        @SneakyThrows
        @Override
        public boolean hasNext() {
            endOfNodes = skipElements();
            return !endOfNodes && reader.hasNext();
        }

        boolean skipElements() throws XMLStreamException {
            int eventType = reader.getEventType();
            boolean noNodes = false;
            while (eventType != END_DOCUMENT
                    && eventType != START_ELEMENT
                    || (eventType == START_ELEMENT && !"node".equals(reader.getLocalName()))
            ) {
                if (eventType == START_ELEMENT) {
                    String s = reader.getLocalName();
                    noNodes = "way".equals(s) || "relation".equals(s);
                    if (noNodes) {
                        break;
                    }
                }
                eventType = reader.next();
            }
            return noNodes;
        }

        @SneakyThrows
        @Override
        public T next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return unmarshaller.unmarshal(reader, clazz).getValue();
        }
    }
}
