/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.datamapper;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.StatementCreatorUtils;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.sql.Types;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Query Type:
 *
 * <ul>
 * <li>TEMPLATE : we receive the string defined in @Query/@QueryOverride annotation.
 * <li>EXECUTE : the query will be executed. If it's a select, the result will be mapped with a
 * ConstructorAnnotationRowMapper if it has the correct form.
 * </ul>
 *
 */
public enum QueryType {
	/**
	 * Receive the string defined in @Query/@QueryOverride annotation.
	 */
	TEMPLATE {
		@Override
		String apply(String template, NamedParameterJdbcTemplate jdbc, Method method, Object[] args) {
			return template;
		}
	},

	/**
	 */
	EXECUTE {

		/**
		 * Keep a mapping between a given class and a possible RowMapper.
		 *
		 * If the Class has the correct form, a ConstructorAnnotationRowMapper will be built and the boolean set to true
		 * in the pair. If the class has not the correct form, the boolean will be false and the class will be used as
		 * it is in the jdbc template.
		 */
		private final Map<Class<Object>, HasRowmapper> cachedClassToMapper = new ConcurrentHashMap<Class<Object>, HasRowmapper>();

		@Override
		Object apply(String template, NamedParameterJdbcTemplate jdbc, Method method, Object[] args) {
			JdbcAction action = actionFromContext(method, template);
			SqlParameterSource parameters = extractParameters(method, args);
            switch(action) {
                case QUERY:
                    return doQuery(template, jdbc, method, parameters);
                case UPDATE:
                    return jdbc.update(template, parameters);
                case INSERT_W_AUTO_GENERATED_KEY:
                    AutoGeneratedKey spec = method.getDeclaredAnnotation(AutoGeneratedKey.class);
                    return executeUpdateAndKeepKeys(template, spec.value(), spec.keyClass(), jdbc, parameters);
                default:
                    throw new IllegalArgumentException("unknown value for action: "+action);
            }
		}

        private <T> Pair<Integer, T> executeUpdateAndKeepKeys(String template,
                                                               String keyName,
                                                               Class<T> keyClass,
                                                               NamedParameterJdbcTemplate jdbc,
                                                               SqlParameterSource parameters) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            int result = jdbc.update(template, parameters, keyHolder);
            final Map<String, Object> keys = keyHolder.getKeys();
            Object key;
            if(keys.size() > 1) {
                key = keys.get(keyName);
            } else {
                key = keys.values().stream().findFirst().orElseThrow(IllegalStateException::new);
            }
            return Pair.of(result, keyClass.cast(key));
        }

        @SuppressWarnings("unchecked")
		private Object doQuery(String template, NamedParameterJdbcTemplate jdbc, Method method,
				SqlParameterSource parameters) {
			if (method.getReturnType().isAssignableFrom(List.class)) {
				Class<Object> c = (Class<Object>) ((ParameterizedType) method.getGenericReturnType())
						.getActualTypeArguments()[0];
				HasRowmapper r = ensurePresence(c);
				if (r.present) {
					return jdbc.query(template, parameters, r.rowMapper);
				} else {
					return jdbc.queryForList(template, parameters, c);
				}
			} else {
				Class<Object> c = (Class<Object>) method.getReturnType();
				HasRowmapper r = ensurePresence(c);
				if (r.present) {
					return jdbc.queryForObject(template, parameters, r.rowMapper);
				} else {
					return jdbc.queryForObject(template, parameters, c);
				}
			}
		}

		private HasRowmapper ensurePresence(Class<Object> c) {
			if (!cachedClassToMapper.containsKey(c)) {
				cachedClassToMapper.put(c, handleClass(c));
			}
			return cachedClassToMapper.get(c);
		}
	};

    private static JdbcAction actionFromContext(Method method, String template) {
        if(method.getDeclaredAnnotation(AutoGeneratedKey.class) == null) {
            return actionFromTemplate(template);
        }
        return JdbcAction.INSERT_W_AUTO_GENERATED_KEY;
    }

	private static JdbcAction actionFromTemplate(String template) {
		String tmpl = StringUtils.deleteAny(template.toLowerCase(Locale.ENGLISH), "() ").trim();
		return tmpl.indexOf("select") == 0 ? JdbcAction.QUERY : JdbcAction.UPDATE;
	}

	private enum JdbcAction {
		QUERY, UPDATE, INSERT_W_AUTO_GENERATED_KEY
	}

	abstract Object apply(String template, NamedParameterJdbcTemplate jdbc, Method method, Object[] args);

	private static HasRowmapper handleClass(Class<Object> c) {
		if (ConstructorAnnotationRowMapper.hasConstructorInTheCorrectForm(c)) {
			return new HasRowmapper(true, new ConstructorAnnotationRowMapper<>(c));
		} else {
			return new HasRowmapper(false, null);
		}
	}

	private static SqlParameterSource extractParameters(Method m, Object[] args) {

		Annotation[][] parameterAnnotations = m.getParameterAnnotations();
		if (parameterAnnotations == null || parameterAnnotations.length == 0) {
			return new EmptySqlParameterSource();
		}

		MapSqlParameterSource ps = new MapSqlParameterSource();
		Class<?>[] parameterTypes = m.getParameterTypes();
		for (int i = 0; i < args.length; i++) {
			String name = parameterName(parameterAnnotations[i]);
            if(name != null) {
                if(args[i] != null && ZonedDateTime.class.isAssignableFrom(parameterTypes[i])) {
                    ZonedDateTime dateTime = ZonedDateTime.class.cast(args[i]);
                    final ZonedDateTime utc = dateTime.withZoneSameInstant(ZoneId.of("UTC"));
                    Calendar c = Calendar.getInstance();
                    c.setTimeZone(TimeZone.getTimeZone("UTC"));
                    c.setTimeInMillis(utc.toInstant().toEpochMilli());
                    ps.addValue(name, c, Types.TIMESTAMP);
                } else {
                    ps.addValue(name, args[i], StatementCreatorUtils.javaTypeToSqlParameterType(parameterTypes[i]));
                }
            }
		}

		return ps;
	}

	private static String parameterName(Annotation[] annotation) {

		if (annotation == null) {
			return null;
		}

		for (Annotation a : annotation) {
			if (a instanceof Bind) {
				return ((Bind) a).value();
			}
		}
		return null;
	}

	private static class HasRowmapper {
		private final boolean present;
		private final RowMapper<Object> rowMapper;

		HasRowmapper(boolean present, RowMapper<Object> rowMapper) {
			this.present = present;
			this.rowMapper = rowMapper;
		}
	}
}
