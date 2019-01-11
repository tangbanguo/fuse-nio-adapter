package org.cryptomator.frontend.fuse.mount;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.HashMap;
import java.util.Map;

class PlistDictParser {

	private static final String ELEM_DICT = "dict";
	private static final String ELEM_KEY = "key";
	private static final String ELEM_ARRAY = "array";
	private static final String ELEM_DATA = "data";
	private static final String ELEM_DATE = "date";
	private static final String ELEM_REAL = "real";
	private static final String ELEM_INTEGER = "integer";
	private static final String ELEM_STRING = "string";
	private static final String ELEM_TRUE = "true";
	private static final String ELEM_FALSE = "false";

	private final XMLStreamReader reader;
	private final Map<String, Plist.Value> dictionary;
	private String key;

	/**
	 * @param reader A {@link XMLStreamReader} currently at the beginning of a new dict element.
	 * @throws IllegalStateException If the reader is not currently positioned at the beginning of a dict.
	 */
	public PlistDictParser(XMLStreamReader reader) throws IllegalStateException {
		if (reader.getEventType() != XMLStreamConstants.START_ELEMENT || !ELEM_DICT.equalsIgnoreCase(reader.getLocalName())) {
			throw new IllegalStateException("Not at beginning of a new <dict>.");
		}
		this.reader = reader;
		this.dictionary = new HashMap<>();
	}

	public Plist.Value parse() throws XMLStreamException {
		while (reader.hasNext()) {
			reader.next();
			switch (reader.getEventType()) {
				case XMLStreamConstants.START_ELEMENT:
					startElement();
					break;
				case XMLStreamConstants.END_ELEMENT:
					if (endOfDict()) {
						return new Plist.Value(Plist.ValueType.DICT, dictionary);
					}
				default:
					break;
			}
		}
		throw new IllegalStateException("Element not ended");
	}

	private void startElement() throws XMLStreamException {
		String element = reader.getLocalName().toLowerCase();
		switch (element) {
			case ELEM_KEY:
				key = reader.getElementText();
				break;
			case ELEM_ARRAY:
				// TODO
				break;
			case ELEM_DICT:
				addElement(new PlistDictParser(reader).parse());
				break;
			case ELEM_STRING:
			case ELEM_DATA:
			case ELEM_DATE:
			case ELEM_INTEGER:
			case ELEM_REAL:
			case ELEM_TRUE:
			case ELEM_FALSE:
				addElement(new PlistValueParser(reader).parse());
				break;
			default:
				throw new XMLStreamException("Unexpected element: " + element);
		}
	}

	private void addElement(Plist.Value value) {
		if (key == null) {
			throw new IllegalStateException("Missing key");
		} else {
			dictionary.put(key, value);
			key = null;
		}
	}

	private boolean endOfDict() {
		return ELEM_DICT.equalsIgnoreCase(reader.getLocalName());
	}

}
