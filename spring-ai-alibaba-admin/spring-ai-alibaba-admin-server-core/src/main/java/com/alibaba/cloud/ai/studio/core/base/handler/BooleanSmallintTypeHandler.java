/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.studio.core.base.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis TypeHandler that maps Java {@link Boolean} to PostgreSQL SMALLINT (1/0).
 * <p>
 * PostgreSQL SMALLINT columns reject Java Boolean values directly (e.g.,
 * {@code column "enabled" is of type smallint but expression is of type boolean}).
 * This handler converts Boolean to Integer (1 for true, 0 for false) on write,
 * and Integer back to Boolean on read.
 *
 * @since 1.0.0.3
 */
@MappedTypes(Boolean.class)
@MappedJdbcTypes(JdbcType.SMALLINT)
public class BooleanSmallintTypeHandler extends BaseTypeHandler<Boolean> {

	@Override
	public void setNonNullParameter(PreparedStatement ps, int i, Boolean parameter, JdbcType jdbcType)
			throws SQLException {
		ps.setInt(i, Boolean.TRUE.equals(parameter) ? 1 : 0);
	}

	@Override
	public Boolean getNullableResult(ResultSet rs, String columnName) throws SQLException {
		int value = rs.getInt(columnName);
		return rs.wasNull() ? null : value == 1;
	}

	@Override
	public Boolean getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
		int value = rs.getInt(columnIndex);
		return rs.wasNull() ? null : value == 1;
	}

	@Override
	public Boolean getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
		int value = cs.getInt(columnIndex);
		return cs.wasNull() ? null : value == 1;
	}

}
