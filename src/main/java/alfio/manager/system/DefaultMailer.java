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
package alfio.manager.system;

import alfio.model.system.ConfigurationKeys;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Component;

import javax.activation.FileTypeMap;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

@Log4j2
@Component
@Profile("!dev")
public class DefaultMailer implements Mailer {

	private final ConfigurationManager configurationManager;

	@Autowired
	public DefaultMailer(ConfigurationManager configurationManager) {
		this.configurationManager = configurationManager;
	}

	@Override
	public void send(String to, String subject, String text, Optional<String> html, Attachment... attachments) {
		
		MimeMessagePreparator preparator = (mimeMessage) -> {
			MimeMessageHelper message = html.isPresent() || !ArrayUtils.isEmpty(attachments) ? new MimeMessageHelper(mimeMessage, true, "UTF-8")
					: new MimeMessageHelper(mimeMessage, "UTF-8");
			message.setSubject(subject);
			message.setFrom(configurationManager.getRequiredValue(ConfigurationKeys.SMTP_FROM_EMAIL));
			message.setTo(to);
			if (html.isPresent()) {
				message.setText(text, html.get());
			} else {
				message.setText(text, false);
			}

			if (attachments != null) {
				for (Attachment a : attachments) {
					message.addAttachment(a.getFilename(), a.getSource(), a.getContentType());
				}
			}
			
			message.getMimeMessage().saveChanges();
			message.getMimeMessage().removeHeader("Message-ID");
		};
		toMailSender().send(preparator);
	}

	private JavaMailSender toMailSender() {
		JavaMailSenderImpl r = new CustomJavaMailSenderImpl();
		r.setDefaultEncoding("UTF-8");
		r.setHost(configurationManager.getRequiredValue(ConfigurationKeys.SMTP_HOST));
		r.setPort(Integer.valueOf(configurationManager.getRequiredValue(ConfigurationKeys.SMTP_PORT)));
		r.setProtocol(configurationManager.getRequiredValue(ConfigurationKeys.SMTP_PROTOCOL));
		r.setUsername(configurationManager.getStringConfigValue(ConfigurationKeys.SMTP_USERNAME, null));
		r.setPassword(configurationManager.getStringConfigValue(ConfigurationKeys.SMTP_PASSWORD, null));

		String properties = configurationManager.getStringConfigValue(ConfigurationKeys.SMTP_PROPERTIES, null);

		if (properties != null) {
			try {
				Properties prop = PropertiesLoaderUtils.loadProperties(new EncodedResource(new ByteArrayResource(
						properties.getBytes("UTF-8")), "UTF-8"));
				r.setJavaMailProperties(prop);
			} catch (IOException e) {
				log.warn("error while setting the mail sender properties", e);
			}
		}
		return r;
	}
	
	static class CustomMimeMessage extends MimeMessage {
		
		private String defaultEncoding;
		private FileTypeMap defaultFileTypeMap;

		CustomMimeMessage(Session session, String defaultEncoding, FileTypeMap defaultFileTypeMap) {
			super(session);
			this.defaultEncoding = defaultEncoding;
			this.defaultFileTypeMap = defaultFileTypeMap;
		}

		CustomMimeMessage(Session session, InputStream contentStream) throws MessagingException {
			super(session, contentStream);
		}
		
		public final String getDefaultEncoding() {
			return this.defaultEncoding;
		}

		public final FileTypeMap getDefaultFileTypeMap() {
			return this.defaultFileTypeMap;
		}
		
		@Override
		protected void updateMessageID() throws MessagingException {
		    removeHeader("Message-Id");
		}
		
		@Override
		public void setHeader(String name, String value) throws MessagingException {
			if(!"Message-Id".equals(name)) {
				super.setHeader(name, value);
			}
		}
	}
	
	static class CustomJavaMailSenderImpl extends JavaMailSenderImpl {
		@Override
		public MimeMessage createMimeMessage() {
			return new CustomMimeMessage(getSession(), getDefaultEncoding(), getDefaultFileTypeMap());
		}
		
		@Override
		public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
			try {
				return new CustomMimeMessage(getSession(), contentStream);
			}
			catch (MessagingException ex) {
				throw new MailParseException("Could not parse raw MIME content", ex);
			}
		}
	}
}