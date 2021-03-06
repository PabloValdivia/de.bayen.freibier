package de.bayen.bx.onlinebanking.model;

import org.compiere.model.MBPBankAccount;

/**
 * I extended the table of {@link MBPBankAccount} but I did not want to exchange
 * the whole model classes. Thats why here are the things I added to this Model
 * for my own purposes.
 * 
 * @author tbayen
 */
public class MBPBankAccountHelper {

	public static final String COLUMNNAME_IBAN="IBAN";
	
	static public enum SepaSddScheme {
		CORE, COR1, B2B
	};

	/**
	 * german "Lastschriftart", has one of the scheme names as string.
	 */
	public static final String COLUMNNAME_SEPASDDSCHEME="SepaSddScheme";
	public static final String COLUMNNAME_ISTRANSFERRED="IsTransferred";
	public static final String COLUMNNAME_MNDTID="MndtId";
	public static final String COLUMNNAME_DATEDOC="DateDoc";
}
