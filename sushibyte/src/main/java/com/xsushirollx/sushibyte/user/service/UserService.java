package com.xsushirollx.sushibyte.user.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.xsushirollx.sushibyte.user.configs.PasswordUtils;
import com.xsushirollx.sushibyte.user.dto.UserDTO;
import com.xsushirollx.sushibyte.user.entities.Customer;
import com.xsushirollx.sushibyte.user.entities.User;
import com.xsushirollx.sushibyte.user.entities.Verification;
import com.xsushirollx.sushibyte.user.repositories.CustomerDAO;
import com.xsushirollx.sushibyte.user.repositories.DriverDAO;
import com.xsushirollx.sushibyte.user.repositories.UserDAO;
import com.xsushirollx.sushibyte.user.repositories.VerificationDAO;

/**
 * @author dyltr 
 * references: https://www.codejava.net/frameworks/spring-boot/email-verification-example
 * Provide business logic for general users        
 */
@Service
public class UserService {
	@Autowired
	private UserDAO userDAO;
	@Autowired
	private CustomerDAO customerDAO;
	@Autowired
	private DriverDAO driverDAO;
	@Autowired
	private VerificationDAO verificationDAO;
	//users mapped with user role
	static Logger log = LogManager.getLogger(UserService.class.getName());

	/**
	 * @param userDTO TODO
	 * @param string 
	 * @param user
	 * @return true if user has successfully been saved
	 */
	@Transactional
	public String registerOnValidation(UserDTO userDTO) {
		if (userDTO==null) {
			return null;
		}
		User user = new User();
		user.setEmail(userDTO.getEmail());
		user.setUsername(userDTO.getUsername());
		user.setPassword(userDTO.getPassword());
		user.setFirstName(userDTO.getFirstName());
		user.setLastName(userDTO.getLastName());
		user.setPhone(userDTO.getPhone());
		user.setUserRole(2);
		if (!validatePassword(user.getPassword())||
				!validateName(user.getFirstName())||
				!validateName(user.getLastName())||
				!validateEmail(user.getEmail())||
				checkEmailExist(user.getEmail())||
				checkPhoneExist(user.getPhone())||
				checkUsernameExist(user.getUsername())||
				!validateUsername(user.getUsername())||
				!validatePhone(user.getPhone())) {
			return null;
		}
		user.setPassword(PasswordUtils.generateSecurePassword(user.getPassword()));
		user.setCreatedAt(Timestamp.from(Instant.now()));
		user.setUserRole(3);
		Verification verification = null;
		try {
			// email validated with hibernate
			user = userDAO.save(user);
			customerDAO.save(new Customer(user.getId()));
			verification=verificationDAO.save(new Verification(user.getId()));
		} catch (Exception e) {
			log.log(Level.WARN, e.getMessage());
		}
		return (verification!=null)?verification.getVerificationCode():null;
	}

	/**
	 * @param email
	 * @return true if meets criteria
	 */
	public boolean validateEmail(String email) {
		String regex = "^([_a-zA-Z0-9-]+(\\.[_a-zA-Z0-9-]+)*@[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)*(\\.[a-zA-Z]{1,6}))?$";
		Pattern pattern = Pattern.compile(regex);
		if (pattern.matcher(email).matches()) {
			return true;
		}
		return false;
	}
	
	/**
	 * @param email
	 * @return true if email exists in database
	 */
	private boolean checkEmailExist(String email) {
		return(userDAO.findByEmail(email) != null)?true:false;
	}
	
	/**
	 * @param email
	 * @return true if phone exists in database
	 */
	private boolean checkPhoneExist(String phone) {
		return(userDAO.findByPhone(phone) != null)?true:false;
	}
	
	/**
	 * @param email
	 * @return true if email exists in database
	 */
	private boolean checkUsernameExist(String username) {
		return(userDAO.findByUsername(username) != null)?true:false;
	}

	/**
	 * @param username
	 * @return true if meets criteria and is unique
	 */
	public boolean validateUsername(String username) {
		if (username == null||username.equals("")) {
			return false;
		}
		return true;
	}

	/**
	 * @param phone
	 * @return true if meets criteria and is unique
	 */
	public boolean validatePhone(String phone) {
		if (phone == null || phone.length() != 10) {
			return false;
		}
		for (char i : phone.toCharArray()) {
			if (!Character.isDigit(i))
				return false;
		}
		return true;
	}

	/**
	 * @param name
	 * @return true if meets criteria
	 */
	public boolean validateName(String name) {
		Pattern pattern = Pattern.compile("[0-9]");
		if (name == null || pattern.matcher(name).find()) {
			return false;
		}
		return true;
	}

	/**
	 * @param password
	 * @return true if meets criteria
	 */
	public boolean validatePassword(String password) {
		if (password == null || password.length() < 6 || password.length() > 20) {
			return false;
		}
		return true;
	}

	/**
	 * removes verification code and set active
	 * @param verificationCode
	 * @return true if email still exists and is currently not active
	 */
	@Transactional
	public boolean verifyUserEmail(String verificationCode) {
		Verification verification = verificationDAO.findByVericationCode(verificationCode);
		if (verification == null) {
			return false;
		}
		if (Duration.between(Instant.now(), verification.getCreatedAt().toInstant()).toMinutes()>10) {
			log.log(Level.DEBUG,"Verification code has expired");
			return false;
		}
		User user = userDAO.findById(verification.getId()).get();
		if (user.isActive()) {
			return false;
		}
		user.setActive(true);
		try{
			userDAO.save(user);
			verificationDAO.delete(verification);
		}
		catch(Exception e) {
			log.log(Level.WARN, e.getMessage() + ":\tVerify user email failed");
		}
		return true;
	}
	
	@Transactional
	public String resetVerificationCode(String email) {
		User user = userDAO.findByEmail(email);
		if (user==null) {
			return null;
		}
		Verification verification = new Verification(user.getId());
		verification = verificationDAO.save(verification);
		return verification.getVerificationCode();
	}

	/**
	 * Only used for admin or unsuccessful email verification
	 * @param user
	 * @return true if user is deleted or not exist
	 */
	@Transactional
	private boolean deleteUserPermanent(Integer id) {
		if (id==null) {
			return false;
		}
		try {
			if(verificationDAO.existsById(id)) {
				verificationDAO.deleteById(id);
			}
			if (driverDAO.existsById(id)) {
				driverDAO.deleteById(id);
			}
			if (customerDAO.existsById(id)) {
				customerDAO.deleteById(id);
			}
			if (userDAO.existsById(id)) {
				userDAO.deleteById(id);
			}
		} catch (Exception e) {
			log.log(Level.INFO, "User not deleted");
			return false;
		}
		log.log(Level.INFO, "User deleted");
		return true;
	}

	/**
	 * @param id
	 * @return true if account has successfully been deactivated
	 */
	@Transactional
	public boolean closeAccount(Integer id) {
		if (userDAO.existsById(id)) {
			User user = userDAO.findById(id).get();
			user.setActive(false);
			try {
				userDAO.save(user);
			}
			catch(Exception e) {
				log.log(Level.WARN, e.getMessage());
				return false;
			}
			return true;
		}
		log.log(Level.INFO,"Id " + id + " does not exist in database");
		return false;
	}
	
	/**
	 * @param id
	 * @return true if account has successfully been reactivated
	 */
	@Transactional
	public boolean reactivateAccount(Integer id) {
		if (userDAO.existsById(id)) {
			User user = userDAO.findById(id).get();
			user.setActive(true);
			try {
				userDAO.save(user);
			}
			catch(Exception e) {
				log.log(Level.WARN, e.getMessage());
				return false;
			}
			return true;
		}
		log.log(Level.INFO,"Id " + id + " does not exist in database");
		return false;
	}
	
	/**
	 * @param id
	 * @param userD
	 * @return true if account has successfully updated
	 */
	@Transactional
	public boolean updateAccount(int id, UserDTO userD) {
		if (!validatePassword(userD.getPassword())||
				!validateName(userD.getFirstName())||
				!validateName(userD.getLastName())||
				!validateEmail(userD.getEmail())||
				!validateUsername(userD.getUsername())||
				!validatePhone(userD.getPhone())) {
			return false;
		}
		if (userDAO.existsById(id)) {
			User user = userDAO.findById(id).get();
			user.setEmail(userD.getEmail());
			user.setFirstName(userD.getFirstName());
			user.setLastName(userD.getLastName());
			user.setPassword(PasswordUtils.generateSecurePassword(userD.getPassword()));
			user.setPhone(userD.getPhone());
			try {
				userDAO.save(user);
			}
			catch(Exception e){
				log.log(Level.WARN,e.getMessage());
				return false;
			}
			return true;
		}
		return false;
	}
	
	public UserDTO getUserInfo(int id) {
		if(userDAO.existsById(id)) {
			User user = userDAO.findById(id).get();
			UserDTO userD = new UserDTO();
			userD.setEmail(user.getEmail());
			userD.setFirstName(user.getFirstName());
			userD.setLastName(user.getLastName());
			//Will not display password as it is hashed anyways
			//userD.setPassword(user.getPassword());
			userD.setPhone(user.getPhone());
			return userD;
		}
		return null;
	}
}
