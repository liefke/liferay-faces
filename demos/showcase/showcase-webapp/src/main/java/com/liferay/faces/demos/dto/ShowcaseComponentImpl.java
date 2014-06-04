/**
 * Copyright (c) 2000-2014 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
package com.liferay.faces.demos.dto;

import java.io.Serializable;
import java.util.List;

import com.liferay.faces.util.lang.StringPool;


/**
 * @author  Neil Griffin
 */
public class ShowcaseComponentImpl implements Serializable, ShowcaseComponent {

	// serialVersionUID
	private static final long serialVersionUID = 1206732371727535048L;

	private String camelCaseName;
	private String fullName;
	private String key;
	private String lowerCaseName;
	private String prefix;
	private boolean resetToString = true;
	private String toString;
	private List<UseCase> useCases;

	public ShowcaseComponentImpl(String prefix, String camelCaseName, String lowerCaseName, List<UseCase> useCases) {
		this.prefix = prefix;
		this.camelCaseName = camelCaseName;
		this.lowerCaseName = lowerCaseName;
		this.useCases = useCases;
		this.fullName = prefix + StringPool.COLON + camelCaseName;
		this.key = prefix + StringPool.DASH + lowerCaseName;
	}

	@Override
	public String toString() {

		if (resetToString) {

			toString = null;

			if ((prefix != null) && (camelCaseName != null)) {

				return prefix + StringPool.COLON + camelCaseName;
			}
		}

		return toString;
	}

	@Override
	public String getCamelCaseName() {
		return camelCaseName;
	}

	@Override
	public String getLowerCaseName() {
		return lowerCaseName;
	}

	@Override
	public String getPrefix() {
		return prefix;
	}

	@Override
	public List<UseCase> getUseCases() {
		return useCases;
	}

	@Override
	public String getFullName() {
		return fullName;
	}

	@Override
	public String getKey() {
		return key;
	}
}
