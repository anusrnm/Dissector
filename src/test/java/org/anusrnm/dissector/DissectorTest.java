package org.anusrnm.dissector;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.Assert.*;

public class DissectorTest {
    private static final Logger log = LoggerFactory.getLogger(DissectorTest.class);
    private final ClassLoader classloader = Thread.currentThread().getContextClassLoader();

    @Ignore
    @Test
    public void testLayout() throws Exception {
        File layoutFile = new File(Objects.requireNonNull(classloader.getResource("COMPLEX.xml")).getFile());
        File inputFile = new File(Objects.requireNonNull(classloader.getResource("COMPLEX_in.txt")).getFile());
        File resultFile = new File(Objects.requireNonNull(classloader.getResource("COMPLEX_result.txt")).getFile());
        String hexString = Files.readString(inputFile.toPath());
        String expected = Files.readString(resultFile.toPath());
        Dissector dissector = new Dissector(layoutFile, "d");
        String actual = dissector.parseWith(hexString);
        log.debug("{}",actual);
        assertEquals(expected,actual);
    }

    @Ignore
    @Test
    public void testParser() throws Exception {
        File layoutFile = new File(Objects.requireNonNull(classloader.getResource("TEST.xml")).getFile());
        String hexString = "00000004C1C2C3C4";
        String expected = """
                                          (0.4) Len : 00000004 = '4'
                ---data Size=4
                                         (4.4) Data : C1C2C3C4 = 'ABCD'
                                         """;
        Dissector dissector = new Dissector(layoutFile, "d");
        String actual = dissector.parseWith(hexString);
        log.debug("{}",actual);
        assertEquals(expected,actual);
    }

    @Test
    public void testGetChildElementsByTagName() throws Exception {
        File layoutFile = new File(Objects.requireNonNull(classloader.getResource("TestField.xml")).getFile());
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(layoutFile);
        List<Element> actual = Dissector.getChildElementsByTagName(doc.getDocumentElement(), "field");
        assertNotNull(actual);
        assertEquals(2, actual.size());
        assertEquals("field", actual.get(0).getTagName());
        assertEquals("field", actual.get(1).getTagName());
    }

    @Test
    public void testGetMatchingElement() throws Exception {
        File layoutFile = new File(Objects.requireNonNull(classloader.getResource("TestField.xml")).getFile());
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(layoutFile);
        Element actual = Dissector.getMatchingChildElement(doc.getDocumentElement(),
                "struc", "name", "myStruc");
        assertNotNull(actual);
        assertEquals("struc", actual.getTagName());
    }

    @Test
    public void testGetNextSiblingHeadElement() throws Exception {
        File layoutFile = new File(Objects.requireNonNull(classloader.getResource("TESTHEAD.xml")).getFile());
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(layoutFile);
        Node actual = Dissector.getNextSiblingHeadElement(doc.getDocumentElement().getChildNodes().item(0));
        assertNotNull(actual);
        assertEquals("nextSiblingHead", actual.getAttributes().getNamedItem("id").getNodeValue());
    }

    @Test
    public void testGetFieldValueInType() {
        String result = Dissector.getInType("4CC1", "PARSD");
        String expected = "20-Oct-2019";
        log.debug("PARSD: {}", result);
        assertEquals(expected, result);
//        result = Dissector.getInType("0000000800000000", "TOD");
//        expected = "01-Jan-1900 00:00:08";
//        log.debug("TOD: {}", result);
//        Assert.assertEquals(expected, result);
        result = Dissector.getInType("D2124223FECD1335", "TOD");
        expected = "09-Feb-2017 07:22:53";
        log.debug("TOD: {}", result);
        assertEquals(expected, result);
        result = Dissector.getInType("0000003C", "ZTOD");
        expected = "03-Jan-1966 01:00:00";
        log.debug("ZTOD: {}", result);
        assertEquals(expected, result);
        result = Dissector.getInType("0190", "MINS");
        expected = "06:40";
        log.debug("MINS: {}", result);
        assertEquals(expected, result);
        result = Dissector.getInType("2F", "B");
        expected = "00101111";
        log.debug("Byte: {}", result);
        assertEquals(expected, result);
        result = Dissector.getInType("2F", "N");
        expected = "2,15";
        log.debug("Nibbles: {}", result);
        assertEquals(expected, result);
        result = Dissector.getInType("C1C2", "C");
        expected = "AB";
        log.debug("Chars: {}", result);
        assertEquals(expected, result);
    }

    @Test
    public void testDateArith() {
        ZonedDateTime date1 = ZonedDateTime.parse("2007-12-03T10:15:30+05:30[Asia/Calcutta]");
        System.out.println("Calcutta: " + date1);

        ZoneId id = ZoneId.of("America/Chicago");
        System.out.println("ZoneId: " + id);

        ZoneId currentZone = ZoneId.systemDefault();
        System.out.println("CurrentZone: " + currentZone);

        Instant timeStamp= Instant.now();
        System.out.println("Machine Time Now:" + timeStamp);

        //timeStamp in zone - "America/Chicago"
        ZonedDateTime CSTZone= timeStamp.atZone(ZoneId.of("America/Chicago"));
        System.out.println("In Chicago(America) Time Zone:"+ CSTZone);

        //timeStamp in zone - "GMT+01:00"
        ZonedDateTime timestampAtGMTPlus1= timeStamp.atZone(ZoneId.of("GMT+01:00"));
        System.out.println("In 'GMT+01:00' Time Zone:"+ timestampAtGMTPlus1);

        //Adding zone to time
        LocalDateTime localDateTime= LocalDateTime.of(2016, 11, 28, 9, 30);
        System.out.println("LocalDateTime is:"+ localDateTime);

        //Adding "America/Los_Angeles" as the Time Zone to localDateTime
        ZonedDateTime zonedDateTime= localDateTime.atZone(ZoneId.of("America/Chicago"));
        System.out.println("In Chicago(America) Time Zone:"+ zonedDateTime);
    }

    @Test
    public void getBitValue() {
        Map<String, String> myMap = new HashMap<>();
        myMap.put("80", "Bit 1");
        myMap.put("40", "Bit 2");
        myMap.put("20", "Bit 3");
        myMap.put("10", "Bit 4");
        myMap.put("08", "Bit 5");
        myMap.put("04", "Bit 6");
        myMap.put("02", "Bit 7");
        myMap.put("01", "Bit 8");
        Collection<String> expected = myMap.values().stream().sorted(Comparator.reverseOrder()).toList();
        List<String> actual = Dissector.getBitValue(0xFF, myMap);
        assertEquals(expected, actual);
    }
}