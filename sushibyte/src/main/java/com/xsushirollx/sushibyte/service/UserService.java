package com.xsushirollx.sushibyte.service;
import java.io.UnsupportedEncodingException;
import java.util.Timer;
import java.util.regex.Pattern;

import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.xsushirollx.sushibyte.entities.Customer;
import com.xsushirollx.sushibyte.entities.User;
import com.xsushirollx.sushibyte.repositories.CustomerDAO;
import com.xsushirollx.sushibyte.repositories.DriverDAO;
import com.xsushirollx.sushibyte.repositories.UserDAO;
import com.xsushirollx.sushibyte.utils.PasswordUtils;

import net.bytebuddy.utility.RandomString;

/**
 * @author dyltr
 * references: https://www.codejava.net/frameworks/spring-boot/email-verification-example
 */
@Service
public class UserService {
	@Autowired
    private JavaMailSender mailSender;
	@Autowired
	private UserDAO u1;
	@Autowired
	private CustomerDAO c1;
	@Autowired
	private DriverDAO d1;
	static Logger log = LogManager.getLogger(UserService.class.getName());
	
	/**
	 * @param user
	 * @return true if user has successfully been saved
	 */
	@Transactional
	public boolean registerOnValidation(User user, String siteUrl) {
		if(!validatePassword(user.getPassword())) {
			return false;
		}
		if(!validateName(user.getFirstName())) {
			return false;
		}
		if(!validateName(user.getLastName())) {
			return false;
		}
		if(!validateEmail(user.getEmail())) {
			return false;
		}
		if(!validateUsername(user.getUsername())) {
			return false;
		}
		if(!validatePhone(user.getPhone())) {
			return false;
		}
		String salt = PasswordUtils.getSalt(30);
		user.setPassword(PasswordUtils.generateSecurePassword(user.getPassword(), salt));
		user.setSalt(salt);
		try {
			//email validated with hibernate
			User user1 = u1.save(user);
			c1.save(new Customer(user1.getId()));
			sendVerificationEmail(user1, siteUrl);
		}
		catch(Exception e) {
			log.debug("Was unable to save user.");
		}
		return true;
	}

	/**
	 * Sends verification email and deletes if not verified in set amount of time
	 * @param user
	 * @param siteUrl
	 * @throws MessagingException
	 * @throws UnsupportedEncodingException
	 */
	private void sendVerificationEmail(User user, String siteUrl) throws MessagingException, UnsupportedEncodingException {
		//implement java mail api
		String verifyURL = siteUrl + "/verify?code=" + user.getVerificationCode();
		MimeMessage message = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(message);
		helper.setFrom("shamila61@iwtclocks.com","xsushirollx");
		helper.setTo(user.getEmail());
		helper.setSubject("Verify your email now.");
		helper.setText("<a href=\\\""+ verifyURL + "\\\" target=\\\"_self\\\">VERIFY</a>",true);
		mailSender.send(message);
		Runnable t1 = new Runnable() {
			@Override
			public void run() {
				try {
					wait(10000);
					final User user1 = u1.findById(user.getId()).get();
					if (user1.isActive()) {
						return;
					}
					deleteUser(user1);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		};
		t1.run();
	}

	public boolean validateEmail(String email) {
		String regex = "^([_a-zA-Z0-9-]+(\\.[_a-zA-Z0-9-]+)*@[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)*(\\.[a-zA-Z]{1,6}))?$";
		Pattern pattern = Pattern.compile(regex);
		if(pattern.matcher(email).matches()) {
			return (u1.findByEmail(email)==null)?true:false;
		}
		return false;
	}

	public boolean validateUsername(String username) {
		if(username==null) {
			return false;
		}
		return (u1.findByUsername(username)==null)?true:false;
	}
	
	public boolean validatePhone(String phone) {
		if(phone==null||phone.length()!=10) {
			return false;
		}
		for(char i:phone.toCharArray()) {
			if(!Character.isDigit(i))
				return false;
		}
		return (u1.findByPhone(phone)==null)?true:false;
	}

	public boolean validateName(String name) {
		Pattern pattern = Pattern.compile("[0-9]");
		if(name==null||pattern.matcher(name).find()) {
			return false;
		}
		return true;
	}

	public boolean validatePassword(String password) {
		if (password==null||password.length()<6||password.length()>20) {
			return false;
		}
		return true;
	}
	
	public boolean verifyUserEmail(String verificationCode) {
		User user = u1.findByVericationCode(verificationCode);
		if (user==null) {
			return false;
		}
		user.setActive(true);
		u1.save(user);
		return true;
	}
	
	/**
	 * Only used for admin or unsuccessful email verification
	 * @param user
	 * @return
	 */
	@Transactional
	public boolean deleteUser(User user) {
		try {
			d1.deleteById(user.getId());
			c1.deleteById(user.getId());;
			u1.delete(user);
		}
		catch(Exception e) {
			return false;
		}
		return true;
	}
}
