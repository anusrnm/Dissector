package org.anusrnm.dissector;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;

class Dissector {

    public static final String DSECT = "dsect";
    public static final String VERSION = "version";
    public static final String COUNTER = "counter";
    public static final String STRUC = "struc";
    public static final String GROUP = "group";
    public static final String LENGTH = "length";
    public static final String FILLER = "filler";
    public static final String START = "start";
    public static final String END = "end";
    public static final String PARSD = "parsd";
    public static final String TOD = "tod";
    public static final String ZTOD = "ztod";
    public static final String MINS = "mins";
    public static final String HHMM = "hhmm";
    private static final String TOD_ADJUST = "1.048576";
    private static final int MAX_COUNTER = 500;
    private final Document doc;
    private final String layoutType;
    private final String layoutDir;
    private final String formatting;
    private StringBuilder res;
    private String inputStr;
    private long displ = 0;
    private long fillerLen = -1;
    private boolean trackLen = false;
    private long useFieldLen = 0;

    Dissector(File layout) throws IOException, SAXException, ParserConfigurationException {
        this(layout, "");
    }

    Dissector(File layout, String formatting) throws IOException, SAXException, ParserConfigurationException {
        this.formatting = formatting;
        layoutDir = layout.getParent();
        doc = getDocument(layout);
        layoutType = doc.getDocumentElement().getAttribute("type");
    }

    public static String getInType(String fieldValue, String fieldType) {
        String fieldValueInType;
        if (fieldValue.isEmpty()) {
            throw new IllegalArgumentException("empty input");
        }
        switch (fieldType.toLowerCase()) {
            case PARSD:
                fieldValueInType = convertToParsDate(fieldValue);
                break;
            case TOD:
                fieldValueInType = convertToTOD(fieldValue);
                break;
            case ZTOD:
                fieldValueInType = convertToZTOD(fieldValue);
                break;
            case MINS:
                int mins = Integer.parseInt(fieldValue, 16);
                fieldValueInType = String.format("%02d:%02d", mins / 60, mins % 60);
                break;
            case HHMM:
                fieldValueInType = convertToHHMM(fieldValue);
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
                try {
                    fieldValueInType = getSafeString(fieldValue);
                } catch (Exception ue) {
                    fieldValueInType = fieldValue;
                }
        }
        return fieldValueInType;
    }

    public static String convertToHHMM(String fieldValue) {
        String fieldValueInType = "";
        if (fieldValue.length() < 4) {
            throw new IllegalArgumentException("minimum 4 hex chars are required");
        }
        int hh = Integer.parseInt(fieldValue.substring(0, 2), 16);
        int mm = Integer.parseInt(fieldValue.substring(2, 4), 16);
        if (hh != 0 || mm != 0)
            fieldValueInType = String.format("%02d:%02d", hh, mm);
        return fieldValueInType;
    }

    public static String convertToZTOD(String fieldValue) {
        String fieldValueInType = "";
        BigInteger ztod = new BigInteger(fieldValue.substring(0, 8), 16);
        if (ztod.intValue() != 0) {
            Calendar cal = new GregorianCalendar(1966, Calendar.JANUARY, 3);
            cal.add(Calendar.MINUTE, ztod.intValue());
            Date newDate = cal.getTime();
            fieldValueInType = new SimpleDateFormat("dd-MMM-yyyy hh:mm:ss").format(newDate);
        }
        return fieldValueInType;
    }

    public static String convertToTOD(String fieldValue) {
        String fieldValueInType = "";
        if (fieldValue.length() < 8) {
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
            fieldValueInType = new SimpleDateFormat("dd-MMM-yyyy hh:mm:ss").format(newDate);
        }
        return fieldValueInType;
    }

    public static String convertToParsDate(String fieldValue) {
        String fieldValueInType = "";
        if (fieldValue.length() < 4) {
            throw new IllegalArgumentException("minimum 4 hex chars are required");
        }
        int parsd = Integer.parseInt(fieldValue, 16);
        if (parsd != 0) {
            Calendar cal = new GregorianCalendar(1966, Calendar.JANUARY, 2);
            cal.add(Calendar.DATE, parsd);
            Date newDate = cal.getTime();
            fieldValueInType = new SimpleDateFormat("dd-MMM-yyyy").format(newDate);
        }
        return fieldValueInType;
    }

    public static Element getMatchingElement(Element parent, String childName, String attrName,
                                             String attrValue, String compareType) {
        List<Element> nl = getChildElementsByTagName(parent, childName);
        return nl.stream()
                .filter(gel -> {
                    if (compareType.equalsIgnoreCase(START)) {
                        return attrValue.toLowerCase().startsWith(gel.getAttribute(attrName).toLowerCase());
                    } else if (compareType.equalsIgnoreCase(END)) {
                        return attrValue.toLowerCase().endsWith(gel.getAttribute(attrName).toLowerCase());
                    } else {
                        return attrValue.equalsIgnoreCase(gel.getAttribute(attrName));
                    }
                })
                .findFirst()
                .orElse(null);
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
        return (Element) next;
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
        for (int j = 0; j < 8; j++) {
            String key = String.format("%02x", 1L << j);
            String val = fieldValuesMap.get(key);
            if (isBitSet(i, j) && val != null) {
                values.add(val);
            }
        }
        return values;
    }

    // Helper method to convert a hexadecimal string to bytes
    public static byte[] hexStringToBytes(String hexString) {
        int len = hexString.length();
        byte[] result = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            result[i / 2] = (byte) Integer.parseInt(hexString.substring(i, i + 2), 16);
        }
        return result;
    }

    public static String getHexDumpWithOffset(byte[] data, int lineLength) {
        StringBuilder result = new StringBuilder();
        int offset = 0;
        while (offset < data.length) {
            StringBuilder hexLine = new StringBuilder();
            StringBuilder charLine = new StringBuilder();

            for (int i = 0; i < lineLength && offset + i < data.length; i++) {
                byte currentByte = data[offset + i];
                String hexValue = String.format("%02X", currentByte);

                hexLine.append(hexValue).append(" ");
                charLine.append(Character.isISOControl((char) currentByte) ? '.' : (char) currentByte);
            }

            result.append(String.format("%08X: %-48s %s%n", offset, hexLine, charLine));
            offset += lineLength;
        }
        return result.toString();
    }

    public static String getHexDump(String hexString) {
        byte[] data;
        try {
            data = new String(hexStringToBytes(hexString), "cp500").getBytes();
        } catch (UnsupportedEncodingException ue) {
            data = hexStringToBytes(hexString);
        }
        return getHexDumpWithOffset(data, 16);
    }

    public static String getSafeString(byte[] data) {
        StringBuilder result = new StringBuilder();
        for (byte currentByte : data) {
            result.append((currentByte >= 32 && currentByte <= 126) ? (char) currentByte : '.');
        }
        return result.toString();
    }

    public static String getSafeString(String hexString) {
        byte[] data;
        try {
            data = new String(hexStringToBytes(hexString), "cp500").getBytes();
        } catch (UnsupportedEncodingException ue) {
            data = hexStringToBytes(hexString);
        }
        return getSafeString(data);
    }

    private Document getDocument(File layout) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        dbFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        return dBuilder.parse(layout);
    }

    private String getFieldValue(long fieldLength) {
        return getFieldValue(fieldLength, true);
    }

    private String getFieldValue(long fieldLength, boolean clearInput) {
        String value;
        if (layoutType.equalsIgnoreCase(DSECT)) {
            fieldLength = fieldLength * 2;
        }
        if (fieldLength > 0 && inputStr.length() >= fieldLength) {
            value = inputStr.substring(0, (int) fieldLength);
            if (clearInput)
                inputStr = inputStr.substring((int) fieldLength);
        } else {
            value = inputStr;
            if (clearInput)
                inputStr = "";
        }
        return value;
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
                    String.format("Warning: No fields found in the layout to parse %n%s%n", getFieldValue(-1)));
        }
        for (Element field : fl) {
            String fieldName = field.getAttribute("name");
            String fieldType = field.getAttribute("type");
            String fieldKind = field.getAttribute("kind");
            String fieldForAttr = field.getAttribute("for");
            String fieldLength = field.getAttribute(LENGTH);
            String fieldValuesAttr = field.getAttribute("values");
            String fieldMinusAttr = field.getAttribute("minus");
            var fieldMinusVal = 0;
            if (!fieldMinusAttr.isEmpty()) {
                try {
                    fieldMinusVal = Integer.parseInt(fieldMinusAttr);
                } catch (Exception any) {
                    res.append(String.format("%nInvalid attribute (minus) for %s %n", fieldName));
                    return -10;
                }
            }
            int fieldLengthInt = 0;
            if (!fieldKind.equalsIgnoreCase(FILLER)) {
                try {
                    fieldLengthInt = Integer.parseInt(fieldLength);
                } catch (NumberFormatException nfe) {
                    res.append(String.format("%nError: Invalid length attribute for %s%n", fieldName));
                    return -10;
                }
            }
            String temp;
            if (formatting.equals("h")) {
                temp = String.format("(%x.%s) %s", displ, fieldLength, fieldName);
            } else if (formatting.equals("d")) {
                temp = String.format("(%d.%s) %s", displ, fieldLength, fieldName);
            } else {
                temp = fieldName;
            }
            if (!fieldLength.isEmpty()) {
                displ += fieldLengthInt;
            }
            String useForFiller = field.getAttribute("useForFiller");
            if (!useForFiller.isEmpty()) {
                trackLen = true;
                fillerLen = 0;
                try {
                    useFieldLen = Integer.parseInt(getInType(getFieldValue(fieldLengthInt, false), "D"));
                } catch (Exception inh) {
                    res.append(String.format("%nError: Invalid Hex. %s%n", inh.getMessage()));
                    return -2;
                }
            }
            if (trackLen && !fieldLength.isEmpty()) {
                fillerLen += fieldLengthInt;
            }
            StringBuilder outputString = new StringBuilder();
            outputString.append(String.format("%35s : ", temp));
            switch (fieldKind) {
                case COUNTER:
                    Integer x4 = handleCounter(parent, field, outputString, fieldLengthInt, fieldForAttr);
                    if (x4 != null) return x4;
                    break;
                case VERSION:
                    Integer x3 = handleVersion(parent, field, outputString, fieldLengthInt, fieldType);
                    if (x3 != null) return x3;
                    break;
                case GROUP:
                    Integer x2 = handleGroup(parent, field, outputString, fieldLengthInt, fieldType);
                    if (x2 != null) return x2;
                    break;
                case LENGTH:
                    Integer x1 = handleLength(parent, field, outputString, fieldLengthInt, fieldType, fieldName, fieldMinusVal, fieldForAttr);
                    if (x1 != null) return x1;
                    break;
                case FILLER:
                    Integer x = handleFiller(parent, field, fieldName, fieldType);
                    if (x != null) return x;
                    break;
                default:
                    Integer x5 = handleField(outputString, fieldLength, fieldLengthInt, fieldValuesAttr, fieldType, fieldName);
                    if (x5 != null) return x5;
            }
        }
        return 0;
    }

    private Integer handleField(StringBuilder outputString, String fieldLength, int fieldLengthInt, String fieldValuesAttr, String fieldType, String fieldName) {
        String fieldValue;
        res.append(outputString);
        if (fieldLength.isEmpty()) {
            res.append("Error: Length attribute not provided\n");
            return -10;
        }
        fieldValue = getFieldValue(fieldLengthInt);
        var fieldValuesMap = convertToMap(fieldValuesAttr);
        var fieldValueMeaning = fieldValuesMap.get(fieldValue);
        if (fieldValueMeaning != null) {
            fieldValueMeaning = String.format(" (%s)", fieldValueMeaning);
        }
        if (fieldType.equalsIgnoreCase("B") && !fieldValuesMap.isEmpty()) {
            int i;
            try {
                i = Integer.parseInt(fieldValue, 16);
            } catch (NumberFormatException nfe) {
                res.append(String.format("%nInvalid data: %s%n", fieldValue));
                return -10;
            }
            List<String> bitValueList = getBitValue(i, fieldValuesMap);
            if (!bitValueList.isEmpty()) {
                if (bitValueList.size() > 1) {
                    fieldValueMeaning = String.format("%n%-35s", String.join("\n", bitValueList));
                } else {
                    fieldValueMeaning = String.format(" (%s)", String.join(",", bitValueList));
                }
            }
        }
        if (layoutType.equalsIgnoreCase(DSECT)) {
            String fit;
            try {
                fit = getInType(fieldValue, fieldType);
            } catch (Exception any) {
                res.append(String.format("%nInvalid data %s %s%n", any.getMessage(), fieldValue));
                return -10;
            }
            String formatterFit = "";
            if (!fit.isEmpty()) {
                formatterFit = String.format(" = '%s'", fit);
            }
            if (fieldValue.length() > 32) {
                res.append(String.format("%n%s%n", getHexDump(fieldValue)));
            } else {
                var fval1 = fieldValuesMap.get(fieldValue);
                var fval = fieldValuesMap.get(fit);
                if (fval1 == null && fval != null) {
                    fieldValueMeaning = String.format(" (%s)", fval);
                }
                res.append(String.format("%s%s%s%n", fieldValue, formatterFit, fieldValueMeaning == null ? "" : fieldValueMeaning));
            }
            if (fieldValue.length() != 2 * fieldLengthInt) {
                res.append(String.format("Warning: %s value not lengthy enough (Current length: %d)%n", fieldName, fieldValue.length() / 2));
                return -11;
            }
        } else {
            if (fieldValueMeaning != null) {
                res.append(String.format("'%s' (%s)%n", fieldValue, fieldValueMeaning));
            } else {
                res.append(String.format("'%s'%n", fieldValue));
            }
            if (fieldValue.length() != fieldLengthInt) {
                res.append(String.format("Warning: %s value not lengthy enough (Current Length: %d%n", fieldName, fieldValue.length()));
                return -11;
            }
        }
        return null;
    }

    private Integer handleCounter(Element parent, Element field, StringBuilder outputString, int fieldLengthInt, String fieldForAttr) {
        res.append(outputString);
        String repeatCount = getFieldValue(fieldLengthInt);
        var headElement = getNextSiblingHeadElement(field);
        var currentStruc = getMatchingElement(parent, STRUC, "name", fieldForAttr, START);
        if (currentStruc == null) {
            res.append(String.format("'%s'%nError: '%s' Struc layout not found.%n", repeatCount, fieldForAttr));
            return -3;
        }
        var iRepeatCount = 0;
        var radix = layoutType.equalsIgnoreCase(DSECT) ? 16 : 10;
        try {
            iRepeatCount = Integer.parseInt(repeatCount, radix);
        } catch (NumberFormatException nfe) {
            res.append(String.format("Invalid counter %s%n", repeatCount));
            return -2;
        }
        if (iRepeatCount > MAX_COUNTER) {
            res.append(String.format("Warning: Counter value %d ('%s') too high (max=%d)%n",
                    iRepeatCount, repeatCount, MAX_COUNTER));
            return -2;
        }
        res.append(String.format("'%s%n", repeatCount));
        if (headElement != null) {
            int ret = parseWith(headElement);
            if (ret != 0) {
                return -1;
            }
        }
        for (int i = 0; i < iRepeatCount; i++) {
            res.append(String.format("%s %d of %d :%n", fieldForAttr, i + 1, iRepeatCount));
            int ret = parseWith(currentStruc);
            if (ret != 0) {
                return -1;
            }
        }
        return null;
    }

    private Integer handleVersion(Element parent, Element field, StringBuilder outputString, int fieldLengthInt, String fieldType) {
        Element headElement;
        res.append(outputString);
        String versionNum = getFieldValue(fieldLengthInt);
        if (layoutType.equalsIgnoreCase(DSECT)) {
            String fit;
            try {
                fit = getInType(versionNum, fieldType);
            } catch (Exception any) {
                res.append(String.format("Invalid hex %s", any.getMessage()));
                return -10;
            }
            res.append(String.format("%s = '%s'%n", versionNum, fit));
        }
        Element currentVersionElement = getMatchingElement(parent, VERSION, "name", versionNum, START);
        headElement = getNextSiblingHeadElement(field);
        if (headElement != null) {
            int ret = parseWith(headElement);
            if (ret != 0) {
                return -1;
            }
        }
        if (currentVersionElement == null) {
            res.append(String.format("Error: '%s' Version layout not found%n", versionNum));
            return -4;
        }
        String includeAttr = currentVersionElement.getAttribute("include");
        if (!includeAttr.isEmpty()) {
            String[] includeVers = includeAttr.split(",");
            res.append(String.format("Includes %d version(s): %s%n", includeVers.length, includeAttr));
            int ret = 0;
            for (String v : includeVers) {
                if (!v.isEmpty()) {
                    Element includedVersionElement = getMatchingElement(parent, VERSION, "name", v, START);
                    if (includedVersionElement != null) {
                        ret = parseWith(includedVersionElement);
                    } else {
                        res.append(String.format("Error: %s version layout not found%n", v));
                        ret = -1;
                    }
                }
                if (ret != 0) {
                    break;
                }
            }
            if (ret != 0) {
                return -1;
            }
        }
        if (parseWith(currentVersionElement) != 0) {
            return -1;
        }
        return null;
    }

    private Integer handleGroup(Element parent, Element field, StringBuilder outputString, int fieldLengthInt, String fieldType) {
        Element headElement;
        res.append(outputString);
        String groupName = getFieldValue(fieldLengthInt);
        if (layoutType.equalsIgnoreCase(DSECT)) {
            String fit;
            try {
                fit = getInType(groupName, fieldType);
            } catch (Exception any) {
                res.append(String.format("%nError: Invalid hex %s%n", any.getMessage()));
                return -10;
            }
            res.append(String.format("%s = '%s'", groupName, fit));
        } else {
            res.append(groupName);
        }
        Element currentGroupElement = getMatchingElement(parent, GROUP, "name", groupName, START);
        if (currentGroupElement == null) {
            currentGroupElement = getMatchingElement(parent, GROUP, "name", "", START); //Get Default group with name= ""
        }
        if (currentGroupElement == null) {
            res.append(String.format("%nError: '%s' Group layout not found%n", groupName));
            return -5;
        }
        String aliasName = currentGroupElement.getAttribute("alias");
        if (!aliasName.isEmpty()) {
            res.append(String.format(" (%s)", aliasName)); //Show Alias name, if any
        }
        res.append("\n");
        headElement = getNextSiblingHeadElement(field);
        if (headElement != null && (parseWith(headElement) != 0)) {
            return -1;
        }
        if (parseWith(currentGroupElement) != 0) {
            return -1;
        }
        return null;
    }

    private Integer handleLength(Element parent, Element field, StringBuilder outputString, int fieldLengthInt, String fieldType, String fieldName, int fieldMinusVal, String fieldForAttr) {
        Element headElement;
        String fieldValue;
        Element currentStruc;
        res.append(outputString);
        fieldValue = getFieldValue(fieldLengthInt);
        int intFieldValue;
        if (layoutType.equalsIgnoreCase(DSECT)) {
            try {
                intFieldValue = Integer.parseInt(fieldValue, 16);
            } catch (NumberFormatException nfe) {
                res.append(String.format("%nError: Invalid hex. %s%n", nfe.getMessage()));
                return -10;
            }
            String fit;
            try {
                fit = getInType(fieldValue, fieldType);
            } catch (Exception any) {
                res.append(String.format("%nError: Invalid hex. %s%n", any.getMessage()));
                return -10;
            }
            res.append(String.format("%s = '%s'%n", fieldValue, fit));
            if (fieldValue.length() / 2 != fieldLengthInt) {
                res.append(String.format("Warning: %s value not lengthy enough (Current length: %d)%n", fieldName, fieldValue.length() / 2));
            }
        } else {
            try {
                intFieldValue = Integer.parseInt(fieldValue, 16);
            } catch (NumberFormatException nfe) {
                res.append(String.format("%nError: Invalid hex. %s%n", nfe.getMessage()));
                return -10;
            }
        }
        String partOfStruc = field.getAttribute("partofstruc");
        if (partOfStruc.equalsIgnoreCase("y")) {
            intFieldValue -= fieldLengthInt;
        }
        intFieldValue -= fieldMinusVal;
        if (intFieldValue > 0) {
            headElement = getNextSiblingHeadElement(field);
            if (headElement != null && (parseWith(headElement) != 0)) {
                return -1;
            }
            String strucValue = getFieldValue(intFieldValue);
            String restOfInput = getFieldValue(-1);
            currentStruc = getMatchingElement(parent, STRUC, "name", fieldForAttr, START);
            if (currentStruc == null) {
                res.append(String.format("Error: '%s' Struc layout not found.%n'%s'%n", fieldForAttr, strucValue));
                return -9;
            }
            res.append(String.format("---%s Size=%d%n", fieldForAttr, intFieldValue));
            inputStr = strucValue;
            int ret = 0;
            while (!inputStr.isEmpty()) {
                ret = parseWith(currentStruc);
                if (ret != 0) {
                    break;
                }
            }
            if (ret != 0) {
                return ret;
            }
            inputStr = restOfInput;
        }
        return null;
    }

    private Integer handleFiller(Element parent, Element field, String fieldName, String fieldType) {
        String fieldValue;
        if (useFieldLen != 0) {
            fillerLen = useFieldLen - fillerLen;
        }
        fieldValue = "";
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
                if (layoutType.equalsIgnoreCase(DSECT)) {
                    opString += String.format("%35s : ", String.format("(%d.%d) %s", displ, fieldValue.length() / 2, fieldName));
                    if (fieldValue.length() > 32) {
                        res.append(String.format("%s%n%s%n", opString, getHexDump(fieldValue)));
                    } else {
                        String fit;
                        try {
                            fit = getInType(fieldValue, fieldType);
                        } catch (Exception any) {
                            res.append(String.format("%nInvalid data %s %s %s%n", fieldName, any.getMessage(), fieldValue));
                            return -10;
                        }
                        res.append(String.format("%s%s = '%s'%n", opString, fieldValue, fit));
                    }
                } else {
                    opString += String.format("%35s: ", String.format("(%d.%d) %s", displ, fieldValue.length(), fieldName));
                    res.append(String.format("%s'%s'%n", opString, fieldValue));
                }
            } else {
                Element currentStruc2 = getMatchingElement(parent, STRUC, "name", strucName, START);
                if (currentStruc2 == null) {
                    try {
                        currentStruc2 = getDocument(Path.of(layoutDir, strucName).toFile()).getDocumentElement();
                    } catch (Exception any) {
                        res.append(String.format("Error: '%s' Struc layout not found. %s%n", strucName, "File not found"));
                        return -6;
                    }
                }
                res.append(String.format("---%s [Rest of the data]:%n", strucName));
                String restOfInput = getFieldValue(-1);
                inputStr = fieldValue;
                int ret1 = 0;
                while (!inputStr.isEmpty()) {
                    ret1 = parseWith(currentStruc2);
                    if (ret1 != 0) {
                        break;
                    }
                }
                inputStr = restOfInput; //Restore
                if (ret1 != 0) {
                    return ret1;
                }
            }
        }
        return null;
    }

}
