package com.goojeans.runserver.util;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum Extension {

	PYTHON3("PYTHON3"),
	JAVA("JAVA"),
	CPP("CPP");

	private String extension;

	public static Extension from(String extension) {
		return Extension.valueOf(extension.toUpperCase());
	}

}
