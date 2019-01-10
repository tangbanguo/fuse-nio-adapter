package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.mount.Plist.ValueType;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

class PlistValueParser {

	private static final String ELEM_DATA = "data";
	private static final String ELEM_DATE = "date";
	private static final String ELEM_REAL = "real";
	private static final String ELEM_INTEGER = "integer";
	private static final String ELEM_STRING = "string";
	private static final String ELEM_TRUE = "true";
	private static final String ELEM_FALSE = "false";

	private final XMLStreamReader reader;
	private ValueType type;
	private StringBuilder contentBuffer;

	PlistValueParser(XMLStreamReader reader) {
		this.reader = reader;
	}

	Plist.Value parse() throws XMLStreamException {
		while (reader.hasNext()) {
			reader.next();
			switch (reader.getEventType()) {
				case XMLStreamConstants.START_ELEMENT:
					type = startElement();
					break;
				case XMLStreamConstants.CDATA:
					parseContent();
					break;
				case XMLStreamConstants.END_ELEMENT:
					Object value = endElement();
					return new Plist.Value(type, value);
				default:
					break;
			}
		}
		throw new IllegalStateException("Element not ended");
	}

	private ValueType startElement() throws XMLStreamException {
		String element = reader.getLocalName().toLowerCase();
		switch (element) {
			case ELEM_STRING:
				contentBuffer = new StringBuilder();
				return ValueType.STRING;
			case ELEM_DATA:
				contentBuffer = new StringBuilder();
				return ValueType.DATA;
			case ELEM_DATE:
				contentBuffer = new StringBuilder();
				return ValueType.DATE;
			case ELEM_INTEGER:
				contentBuffer = new StringBuilder();
				return ValueType.INTEGER;
			case ELEM_REAL:
				contentBuffer = new StringBuilder();
				return ValueType.REAL;
			case ELEM_TRUE:
				return ValueType.TRUE;
			case ELEM_FALSE:
				return ValueType.FALSE;
			default:
				throw new XMLStreamException("Unexpected primitive value: " + element);
		}
	}

	private void parseContent() {
		contentBuffer.append(reader.getTextCharacters());
	}

	private Object endElement() throws XMLStreamException {
		String element = reader.getLocalName().toLowerCase();
		switch (element) {
			case ELEM_STRING:
				return contentBuffer.toString();
			case ELEM_TRUE:
				return Boolean.TRUE;
			case ELEM_FALSE:
				return Boolean.FALSE;
			case ELEM_DATA:
				// TODO
			case ELEM_DATE:
				// TODO
			case ELEM_INTEGER:
				// TODO
			case ELEM_REAL:
				// TODO
			default:
				throw new XMLStreamException("Unexpected primitive value: " + element);
		}
	}

}
