package org.anusrnm.dissector;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

class Dissector {

    private static final String LS = System.lineSeparator();
    private static final SimpleDateFormat yyyyMMdd = new SimpleDateFormat("yyyy.MM.dd");
    private static final SimpleDateFormat ddMMMyyyy = new SimpleDateFormat("dd-MMM-yyyy");
    private static final SimpleDateFormat ddMMMyyyy_hhmmss = new SimpleDateFormat("dd-MMM-yyyy hh:mm:ss");
    private static final GregorianCalendar TPF_EPOCH = new GregorianCalendar(1966, Calendar.FEBRUARY, 2);
    private static final GregorianCalendar TOD_EPOCH = new GregorianCalendar(1900, Calendar.FEBRUARY, 1);
    private static final String TOD_ADJUST = "1.048576";
    private final StringBuilder res;
    private String inputStr;
    private final Document doc;
    private final String layoutType;
    private long displ = 0;
    private long fillerLen = -1;
    private boolean trackLen = false;
    private long useFieldLen = 0;
    private final String folder;

    Dissector(File layout) throws IOException, SAXException, ParserConfigurationException {
        this.folder = layout.getParent();
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        this.doc = dBuilder.parse(layout);
        this.layoutType = doc.getDocumentElement().getAttribute("type");
        this.res = new StringBuilder();
    }

    static String getInType(String fieldValue, String fieldType) {
        String fieldValueInType = "";
        if (fieldValue.isEmpty()) {
            return "";
        }
        switch (fieldType.toLowerCase()) {
            case "parsd":
                int parsd = Integer.parseInt(fieldValue, 16);
                if (parsd != 0) {
                    Calendar cal = new GregorianCalendar(1966, Calendar.JANUARY, 2);
                    cal.add(Calendar.DATE, parsd);
                    Date newDate = cal.getTime();
                    fieldValueInType = ddMMMyyyy.format(newDate);
                }
                break;
            case "tod":
                BigInteger tod = new BigInteger(fieldValue.substring(0, 8), 16);
                if (tod.intValue() != 0) {
                    BigDecimal actualSeconds = new BigDecimal(TOD_ADJUST).multiply(new BigDecimal(tod));
                    BigDecimal minutes = actualSeconds.divide(new BigDecimal(60), RoundingMode.CEILING);
                    BigDecimal seconds = actualSeconds.remainder(new BigDecimal(60));
                    Calendar cal = new GregorianCalendar(1900, Calendar.JANUARY, 1);
                    cal.add(Calendar.MINUTE, minutes.intValue());
                    cal.add(Calendar.SECOND, seconds.intValue());
                    Date newDate = cal.getTime();
                    fieldValueInType = ddMMMyyyy_hhmmss.format(newDate);
                }
                break;
            case "ztod":
                BigInteger ztod = new BigInteger(fieldValue.substring(0, 8), 16);
                if (ztod.intValue() != 0) {
                    Calendar cal = new GregorianCalendar(1966, Calendar.JANUARY, 3);
                    cal.add(Calendar.MINUTE, ztod.intValue());
                    Date newDate = cal.getTime();
                    fieldValueInType = ddMMMyyyy_hhmmss.format(newDate);
                }
                break;
            case "mins":
                int mins = Integer.parseInt(fieldValue, 16);
                fieldValueInType = String.format("%02d:%02d", mins / 60, mins % 60);
                break;
            case "hhmm":
                int hh = Integer.parseInt(fieldValue.substring(0, 2), 16);
                int mm = Integer.parseInt(fieldValue.substring(2, 4), 16);
                if (hh != 0 || mm != 0)
                    fieldValueInType = String.format("%02d:%02d", hh, mm);
                break;
            case "b":
                int b = Integer.parseUnsignedInt(fieldValue, 16);
                fieldValueInType = String.format("%8s", Integer.toBinaryString((b + 256) % 256))
                        .replace(' ', '0');
                break;
            case "d":
                fieldValueInType = String.valueOf(Integer.parseInt(fieldValue, 16));
                break;
            case "n":
                byte n = (byte) Integer.parseUnsignedInt(fieldValue, 16);
                int highNibble = n >> 4;
                int lowNibble = n & 0x0F;
                fieldValueInType = String.format("%d,%d", highNibble, lowNibble);
                break;
            default:
                fieldValueInType = fieldValue;
        }
        return fieldValueInType;
    }

    public static Element getMatchingElement(Element parent, String childName, String attrName,
                                             String attrValue, String compareType) {
        Element matchingElement = null;
        List<Element> nl = getChildElementsByTagName(parent, childName);
        switch (compareType) {
            case "start":
                for (Element gel : nl) {
                    if (gel.getAttribute(attrName).startsWith(attrValue)) {
                        matchingElement = gel;
                        break;
                    }
                }
                break;
            case "end":
                for (Element gel : nl) {
                    if (gel.getAttribute(attrName).endsWith(attrValue)) {
                        matchingElement = gel;
                        break;
                    }
                }
                break;
            default:
                for (Element gel : nl) {
                    if (gel.getAttribute(attrName).equals(attrValue)) {
                        matchingElement = gel;
                        break;
                    }
                }
        }
        return matchingElement;
    }

    public static List<Element> getChildElementsByTagName(Element element, String tagName) {
        ArrayList<Element> childElements = new ArrayList<>();
        NodeList childNodeList = element.getChildNodes();
        for (int i = 0; i < childNodeList.getLength(); i++) {
            Node childNode = childNodeList.item(i);
            String childNodeName = childNode.getNodeName();
            if (childNode.getNodeType() == Node.ELEMENT_NODE
                    && childNodeName.equalsIgnoreCase(tagName)) {
                childElements.add((Element) childNode);
            }
        }
        return childElements;
    }

    public static Element getMatchingChildElement(Element parent, String childElementName,
                                                  String attrName, String attrValue) {
        Element matchingElement = null;
        List<Element> children = getChildElementsByTagName(parent, childElementName);
        for (Element child : children) {
            if (child.getAttribute(attrName).equals(attrValue)) {
                matchingElement = child;
                break;
            }
        }
        return matchingElement;
    }

    public static Element getNextSiblingHeadElement(Node currentNode) {
        Node next;

        while (true) {
            next = currentNode.getNextSibling();
            //Find an Element node with name as head
            if (next != null && (next.getNodeType() != Node.ELEMENT_NODE ||
                    !next.getNodeName().equals("head"))) {
                currentNode = next;
            } else {
                break;
            }
        }
        if (next != null && !next.getNodeName().equals("head")) {
            next = null;
        }
        return (Element) next;
    }

    private String getFieldValue(long fieldLength) {
        return getFieldValue(fieldLength, false);
    }

    private String getFieldValue(long fieldLength, boolean clearInput) {
        String value = "";
        if (layoutType.equalsIgnoreCase("dsect")) {
            fieldLength = fieldLength * 2;
        }
        if (fieldLength > 0 && inputStr.length() >= fieldLength) {
            value = inputStr.substring(0, (int) fieldLength);
        } else {
            value = inputStr;
            if (clearInput)
                inputStr = "";
        }
        return value;
    }

    String parseWith(String hexString) {
        this.inputStr = hexString;
        parseWith(doc.getDocumentElement());
        return res.toString();
    }

    private int parseWith(Element root) {
        List<Element> fl = getChildElementsByTagName(root, "field");
        if (fl.isEmpty()) {
            res.append(
                    String.format("Warning: No fields found in the layout to parse %s%s%s", LS, getFieldValue(-1), LS));
        }
        for (Element field : fl) {
            String fieldName = field.getAttribute("name");
            String fieldType = field.getAttribute("type");
            String fieldKind = field.getAttribute("kind");
            String fieldLength = field.getAttribute("length");
            String fieldValuesAttr = field.getAttribute("values");
            String fieldMinusAttr = field.getAttribute("minus");
            String fieldSearchTypeAttr = field.getAttribute("searchtype");
            int fieldLengthInt = 0;
            if (!fieldKind.equalsIgnoreCase("filler")) {
                try {
                    fieldLengthInt = Integer.parseInt(fieldLength);
                } catch (NumberFormatException nfe) {
                    res.append(String.format("%sError: Invalid length attribute for %s%s",
                            LS, fieldName, LS));
                    return -10;
                }
            }
            String temp = String.format("(%d.%s) %s", displ, fieldLength, fieldName);
            if (!fieldLength.isEmpty()) {
                displ += fieldLengthInt;
            }
            String useForFiller = field.getAttribute("useForFiller");
            if (!useForFiller.isEmpty()) {
                trackLen = true;
                fillerLen = 0;
                try {
                    useFieldLen = Integer.parseInt(getInType(getFieldValue(fieldLengthInt, true), "D"));
                } catch (Exception inh) {
                    res.append(String.format("%sError: Invalid Hex. %s%s", LS, inh.getMessage(), LS));
                    return -2;
                }
            }
            if (trackLen & !fieldLength.isEmpty()) {
                fillerLen += fieldLengthInt;
            }
            res.append(String.format("%35s : ", temp));
        }
        return 0;
    }

}
