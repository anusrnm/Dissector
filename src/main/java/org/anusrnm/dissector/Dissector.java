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
import java.util.*;

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
    private static final int MAX_COUNTER = 500;
    private StringBuilder res;
    private String inputStr;
    private final Document doc;
    private final String layoutType;
    private long displ = 0;
    private long fillerLen = -1;
    private boolean trackLen = false;
    private long useFieldLen = 0;
    private final String folder;
    private final String formatting = "d";

    Dissector(File layout) throws IOException, SAXException, ParserConfigurationException {
        this.folder = layout.getParent();
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        this.doc = dBuilder.parse(layout);
        this.layoutType = doc.getDocumentElement().getAttribute("type");
    }

    public static String getInType(String fieldValue, String fieldType) {
        String fieldValueInType = "";
        if (fieldValue.isEmpty()) {
            throw new IllegalArgumentException("empty input");
        }
        switch (fieldType.toLowerCase()) {
            case "parsd":
                if (fieldValue.length() < 4) {
                    throw new IllegalArgumentException("minimum 4 hex chars are required");
                }
                int parsd = Integer.parseInt(fieldValue, 16);
                if (parsd != 0) {
                    Calendar cal = new GregorianCalendar(1966, Calendar.JANUARY, 2);
                    cal.add(Calendar.DATE, parsd);
                    Date newDate = cal.getTime();
                    fieldValueInType = ddMMMyyyy.format(newDate);
                }
                break;
            case "tod":
                if  (fieldValue.length() < 8) {
                    throw new IllegalArgumentException("minimum 8 hex chars are required");
                }
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
                if  (fieldValue.length() < 4) {
                    throw new IllegalArgumentException("minimum 4 hex chars are required");
                }
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

    public static Map<String, String> convertToMap(String input) {
        Map<String, String> resultMap = new HashMap<>();
        String[] pairs = input.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                resultMap.put(keyValue[0], keyValue[1]);
            }
        }
        return resultMap;
    }

    public static boolean isBitSet(long number, int bitPosition) {
        // Shift 1 to the left by the specified bit position
        long mask = 1L << bitPosition;
        // Perform bitwise AND with the number
        return (number & mask) != 0;
    }

    public static List<String> getBitValue(int i, Map<String, String> fieldValuesMap) {
        List<String> values = new ArrayList<>();
        String val = fieldValuesMap.get("80");
        if (isBitSet(i,7) && val != null) {
            values.add(val);
        }
        val = fieldValuesMap.get("40");
        if (isBitSet(i,6) && val != null) {
            values.add(val);
        }
        val = fieldValuesMap.get("20");
        if (isBitSet(i,5) && val != null) {
            values.add(val);
        }
        val = fieldValuesMap.get("10");
        if (isBitSet(i,4) && val != null) {
            values.add(val);
        }
        val = fieldValuesMap.get("08");
        if (isBitSet(i,3) && val != null) {
            values.add(val);
        }
        val = fieldValuesMap.get("04");
        if (isBitSet(i,2) && val != null) {
            values.add(val);
        }
        val = fieldValuesMap.get("02");
        if (isBitSet(i,1) && val != null) {
            values.add(val);
        }
        val = fieldValuesMap.get("01");
        if (isBitSet(i,0) && val != null) {
            values.add(val);
        }
        return values;
    }

    String parseWith(String hexString) {
        res = new StringBuilder();
        this.inputStr = hexString;
        parseWith(doc.getDocumentElement());
        return res.toString();
    }

    private int parseWith(Element parent) {
        List<Element> fl = getChildElementsByTagName(parent, "field");
        if (fl.isEmpty()) {
            res.append(
                    String.format("Warning: No fields found in the layout to parse %s%s%s", LS, getFieldValue(-1), LS));
        }
        for (Element field : fl) {
            String fieldName = field.getAttribute("name");
            String fieldType = field.getAttribute("type");
            String fieldKind = field.getAttribute("kind");
            String fieldForAttr = field.getAttribute("for");
            String fieldLength = field.getAttribute("length");
            String fieldValuesAttr = field.getAttribute("values");
            String fieldMinusAttr = field.getAttribute("minus");
            String fieldSearchTypeAttr = field.getAttribute("searchtype");
            var fieldMinusVal = 0;
            if (!fieldMinusAttr.isEmpty()) {
                try {
                    fieldMinusVal = Integer.parseInt(fieldMinusAttr);
                }catch (Exception any) {
                    res.append(String.format("\nInvalid attribute (minus) for %s \n", fieldName ));
                    return -10;
                }
            }
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
            String temp = String.format(formatting.equals("h")?"(%x.%s) %s": "(%d.%s) %s", displ, fieldLength, fieldName);
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
            StringBuilder outputString = new StringBuilder();
            outputString.append(String.format("%35s : ", temp));
            switch (fieldKind){
                case "counter":  res.append(outputString);
                    String repeatCount = getFieldValue(fieldLengthInt);
                    var headElement = getNextSiblingHeadElement(field);
                    var currentStruc = getMatchingElement(parent, "struc", "name", fieldForAttr,"start");
                    if (currentStruc == null) {
                        res.append(String.format("'%s'\nError: '%s' Struc layout not found.\n", repeatCount, fieldForAttr));
                        return -3;
                    }
                    var iRepeatCount = 0;
                    var radix = layoutType.equalsIgnoreCase("dsect")? 16: 10;
                    try {
                        iRepeatCount = Integer.parseInt(repeatCount, radix);
                    }catch (NumberFormatException nfe) {
                        res.append(String.format("Invalid counter %s\n", repeatCount));
                        return -2;
                    }
                    if (iRepeatCount > MAX_COUNTER) {
                        res.append(String.format("Warning: Counter value %d ('%s') too high (max=%d)\n",
                                iRepeatCount, repeatCount, MAX_COUNTER));
                        return -2;
                    }
                    res.append(String.format("'%s\n", repeatCount));
                    if (headElement != null) {
                        int ret = parseWith(headElement);
                        if (ret != 0) {
                            return -1;
                        }
                    }
                    for (int i=0; i< iRepeatCount; i++) {
                        res.append(String.format("%s %d of %d :\n", fieldForAttr, i+1, iRepeatCount));
                        int ret = parseWith(currentStruc);
                        if (ret != 0) {
                            return -1;
                        }
                    }
                    break;
                case "version":
                    break;
                case "group":
                    break;
                case "length":
                    break;
                case "filler":
                    if (useFieldLen != 0) {
                        fillerLen = useFieldLen - fillerLen;
                    }
                    String fieldValue = "";
                    if (fillerLen != 0) {
                        fieldValue = getFieldValue(fillerLen);
                    }
                    trackLen = false;
                    fillerLen = -1;
                    useFieldLen = 0;
                    if (!fieldValue.isEmpty()) {
                        String strucName = field.getAttribute("for");
                        if (strucName.isEmpty()) {
                            String opString = "";
                            if (layoutType.equalsIgnoreCase("dsect")) {
                                opString += String.format("%35s : ", String.format("(%d.%d) %s", displ, fieldValue.length()/2, fieldName));
                                String fit;
                                try {
                                    fit = getInType(fieldValue, fieldType);
                                } catch (Exception any){
                                    res.append(String.format("\nInvalid data %s %s %s\n", fieldName, any.getMessage(), fieldValue));
                                    return -10;
                                }
                                res.append(String.format("%s%s = '%s'\n", opString, fieldValue, fit));
                            } else {
                                opString += String.format("%35s: ", String.format("(%d.%d) %s", displ, fieldValue.length(), fieldName));
                                res.append(String.format("%s'%s'\n", opString, fieldValue));
                            }
                        } else {
                            Element currentStruc2 = getMatchingElement(parent, "struc", "name", strucName, "start");
                            if (currentStruc2 == null) { //  TODO: try the name as layout file
                                res.append(String.format("Error: '%s' Struc layout not found. %s\n", strucName, "File not found");
                                return -6;
                            }
                            res.append(String.format("---%s [Rest of the data]:\n", strucName));
                            String restOfInput = getFieldValue(-1);
                            inputStr = fieldValue;
                            int ret = 0;
                            while(!inputStr.isEmpty()) {
                                ret = parseWith(currentStruc2);
                                if (ret != 0) {
                                    break;
                                }
                            }
                            inputStr = restOfInput; //Restore
                            if (ret != 0) {
                                return ret;
                            }
                        }
                    }
                    break;
                default:
                    res.append(outputString);
                    if (fieldLength.isEmpty()) {
                        res.append("Error: Length attribute not provided\n");
                        return -10;
                    }
                    var fieldValue = getFieldValue(fieldLengthInt);
                    fieldValuesAttr = field.getAttribute("values");
                    var fieldValuesMap = convertToMap(fieldValuesAttr);
                    var fieldValueMeaning = fieldValuesMap.get(fieldValue);
                    if (fieldValueMeaning != null) {
                        fieldValueMeaning = String.format(" (%s)", fieldValueMeaning);
                    }
                    if (fieldType.equalsIgnoreCase("B") && !fieldValuesMap.isEmpty()) {
                        int i;
                        try {
                            i = Integer.parseInt(fieldValue, 16);
                        }catch (NumberFormatException nfe) {
                            res.append(String.format("\nInvalid data: %s\n", fieldValue));
                            return -10;
                        }
                        List<String> bitValueList = getBitValue(i, fieldValuesMap);
                        if (!bitValueList.isEmpty()) {
                            if (bitValueList.size() > 1) {
                                fieldValueMeaning = "\n" + String.format("%-35s", String.join("\n", bitValueList));
                            } else {
                                fieldValueMeaning = String.format(" (%s)", String.join(",", bitValueList));
                            }
                        }
                    }
                    if (layoutType.equalsIgnoreCase("dsect")) {
                        String fit;
                        try {
                            fit = getInType(fieldValue, fieldType);
                        } catch (Exception any) {
                            res.append(String.format("\nInvalid data %s %s\n", any.getMessage(), fieldValue));
                            return -10;
                        }
                        String formatterFit = "";
                        if (!fit.isEmpty()) {
                            formatterFit = String.format(" = '%s'", fit);
                        }
                        var fval1 = fieldValuesMap.get(fieldValue);
                        var fval = fieldValuesMap.get(fit);
                        if (fval1 == null && fval != null) {
                            fieldValueMeaning = String.format(" (%s)", fval);
                        }
                        res.append(String.format("%s%s%s\n", fieldValue, formatterFit, fieldValueMeaning));

                        if (fieldValue.length() != 2* fieldLengthInt) {
                            res.append(String.format("Warning: %s value not lengthy enough (Current length: %d)\n", fieldName, fieldValue.length()/2));
                            return -11;
                        }
                    } else {
                        if (fieldValueMeaning != null) {
                            res.append(String.format("'%s' (%s)\n", fieldValue, fieldValueMeaning));
                        } else {
                            res.append(String.format("'%s'\n", fieldValue));
                        }
                        if (fieldValue.length() != fieldLengthInt) {
                            res.append(String.format("Warning: %s value not lengthy enough (Current Length: %d\n", fieldName, fieldValue.length()));
                            return -11;
                        }
                    }
            }
        }
        return 0;
    }

}
