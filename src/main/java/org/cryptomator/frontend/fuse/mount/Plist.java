package org.cryptomator.frontend.fuse.mount;

import java.time.Instant;
import java.util.Map;

public class Plist {

	enum ValueType {
		DICT(Map.class),
		ARRAY(Value[].class),
		DATA(byte[].class),
		DATE(Instant.class),
		REAL(Double.class),
		INTEGER(Integer.class),
		STRING(String.class),
		TRUE(Boolean.class),
		FALSE(Boolean.class);

		private final Class<?> clazz;

		ValueType(Class<?> clazz) {
			this.clazz = clazz;
		}

		boolean matchesType(Object value) {
			return clazz.isInstance(value);
		}

	}

	public static class Value {

		private final ValueType type;
		private final Object value;

		Value(ValueType type, Object value) {
			if (type.matchesType(value)) {
				this.type = type;
				this.value = value;
			} else {
				throw new IllegalArgumentException("Invalid value for type " + type + ": " + value.getClass());
			}
		}

		public String getString() {
			if (type == ValueType.STRING) {
				return (String) value;
			} else {
				throw new UnsupportedOperationException();
			}
		}

		public byte[] getData() {
			if (type == ValueType.DATA) {
				return (byte[]) value;
			} else {
				throw new UnsupportedOperationException();
			}
		}

		public Boolean getBoolean() {
			if (type == ValueType.TRUE) {
				return Boolean.TRUE;
			} else if (type == ValueType.FALSE) {
				return Boolean.FALSE;
			} else {
				throw new UnsupportedOperationException();
			}
		}

		public Map<String, Value> getDict() {
			if (type == ValueType.DICT) {
				return (Map<String, Value>) value;
			} else {
				throw new UnsupportedOperationException();
			}
		}
	}


}
