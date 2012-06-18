/*************************************************************************
 *                                                                       *
 *  EJBCA: The OpenSource Certificate Authority                          *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/

package org.ejbca.ui.cli.ca;

import java.io.File;
import java.security.InvalidParameterException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang.StringUtils;
import org.cesecore.certificates.ca.CAConstants;
import org.cesecore.certificates.ca.CADoesntExistsException;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.ca.CVCCAInfo;
import org.cesecore.certificates.ca.CaSessionRemote;
import org.cesecore.certificates.ca.X509CAInfo;
import org.cesecore.certificates.ca.catoken.CAToken;
import org.cesecore.certificates.ca.catoken.CATokenConstants;
import org.cesecore.certificates.ca.catoken.CATokenInfo;
import org.cesecore.certificates.ca.extendedservices.ExtendedCAServiceInfo;
import org.cesecore.certificates.certificate.CertificateConstants;
import org.cesecore.certificates.certificateprofile.CertificatePolicy;
import org.cesecore.certificates.certificateprofile.CertificateProfile;
import org.cesecore.certificates.certificateprofile.CertificateProfileConstants;
import org.cesecore.certificates.certificateprofile.CertificateProfileSessionRemote;
import org.cesecore.certificates.util.AlgorithmConstants;
import org.cesecore.keys.token.CryptoToken;
import org.cesecore.keys.token.SoftCryptoToken;
import org.cesecore.keys.util.KeyTools;
import org.cesecore.util.CertTools;
import org.cesecore.util.CryptoProviderTools;
import org.cesecore.util.EjbRemoteHelper;
import org.cesecore.util.FileTools;
import org.cesecore.util.SimpleTime;
import org.cesecore.util.StringTools;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionRemote;
import org.ejbca.core.model.ca.caadmin.extendedcaservices.CmsCAServiceInfo;
import org.ejbca.core.model.ca.caadmin.extendedcaservices.HardTokenEncryptCAServiceInfo;
import org.ejbca.core.model.ca.caadmin.extendedcaservices.KeyRecoveryCAServiceInfo;
import org.ejbca.core.model.ca.caadmin.extendedcaservices.OCSPCAServiceInfo;
import org.ejbca.core.model.ca.caadmin.extendedcaservices.XKMSCAServiceInfo;
import org.ejbca.ui.cli.CliUsernameException;
import org.ejbca.ui.cli.ErrorAdminCommandException;
import org.ejbca.ui.cli.IllegalAdminCommandException;
import org.ejbca.util.CliTools;

/**
 * CLI command for initializing initializing CAs.
 * 
 * @version $Id$
 *
 */

enum CaType {
    X509("x509"), CVC("cvc");

    private static Map<String, CaType> lookupMap;
    private final String typeName;

    static {
        lookupMap = new HashMap<String, CaType>();
        for (CaType type : CaType.values()) {
            lookupMap.put(type.getTypeName(), type);
        }
    }

    private CaType(String name) {
        this.typeName = name;
    }

    public String getTypeName() {
        return this.typeName;
    }

    public static CaType lookupCaType(String typeName) {
        return lookupMap.get(typeName);
    }

    public static String getTypeNames() {
        StringBuilder stringBuilder = new StringBuilder("[");
        for (CaType type : CaType.values()) {
            stringBuilder.append(type.getTypeName());
            stringBuilder.append(",");
        }
        stringBuilder.setCharAt(stringBuilder.length() - 1, ']');
        return stringBuilder.toString();
    }
}

/**
 * Create a CA and its first CRL. Publishes the CRL and CA certificate
 *
 * @version $Id$
 */
public class CaInitCommand extends BaseCaAdminCommand {

    public String getMainCommand() {
        return MAINCOMMAND;
    }

    public String getSubCommand() {
        return "init";
    }

    public String getDescription() {
        return "Create a CA and its first CRL. Publishes the CRL and CA certificate";
    }

    public void execute(String[] args) throws ErrorAdminCommandException {
        // Install BC provider
        CryptoProviderTools.installBCProvider();
        try {
            args = parseUsernameAndPasswordFromArgs(args);
        } catch (CliUsernameException e) {
            return;
        }
        // Create new CA.
        if (args.length < 10) {
            getLogger().info("Description: " + getDescription());
            getLogger()
                    .info("Usage: "
                            + getCommand()
                            + " <caname> <dn> <catokentype> <catokenpassword> <keyspec> <keytype> <validity-days> <policyID> <signalgorithm> [-certprofile profileName] [-type  "+ CaType.getTypeNames()+"] [-superadmincn SuperAdmin] [<catokenproperties> or null] [<signed by caid>]");
            getLogger()
                    .info(" catokentype defines if the CA should be created with soft keys or on a HSM. Use 'soft' for software keys and 'org.cesecore.keys.token.PKCS11CryptoToken' for PKCS#11 HSMs.");
            getLogger()
                    .info(" catokenpassword is the password for the CA token. Set to 'null' to use the default system password for Soft token CAs. Set to 'prompt' to prompt for the password on the terminal.");
            getLogger()
                    .info(" catokenpassword is the password for the CA token. Set to 'null' to use the default system password for Soft token CAs");
            getLogger().info(" keytype is RSA, DSA or ECDSA.");
            getLogger().info(" keyspec for RSA keys is size of RSA keys (1024, 2048, 4096, 8192).");
            getLogger().info(" keyspec for DSA keys is size of DSA keys (1024).");
            getLogger().info(" keyspec for ECDSA keys is name of curve or 'implicitlyCA', see docs.");
            StringBuilder typesStringBuilder = new StringBuilder();
            CaType[] typeArray = CaType.values();
            for(int i = 0; i < typeArray.length; ++i) {
                CaType type = typeArray[i];
                typesStringBuilder.append(type.getTypeName());
                if(i == typeArray.length-2) {
                    typesStringBuilder.append(" or ");
                } else if(i == typeArray.length-1) {
                    break;
                } else {
                    typesStringBuilder.append(",");
                } 
            }
            getLogger().info(" type is the CA type. May be [" + typesStringBuilder.toString() + "]. Optional parameter, defaults to x509.");
            getLogger()
                    .info(" policyId can be 'null' if no Certificate Policy extension should be present, or\nobjectID as '2.5.29.32.0' or objectID and cpsurl as \"2.5.29.32.0 http://foo.bar.com/mycps.txt\".");
            getLogger()
                    .info("    you can add multiple policies such as \"2.5.29.32.0 http://foo.bar.com/mycps.txt 1.1.1.1.1 http://foo.bar.com/111cps.txt\".");
            String availableSignAlgs = "";
            for (String algorithm : AlgorithmConstants.AVAILABLE_SIGALGS) {
                availableSignAlgs += (availableSignAlgs.length() == 0 ? "" : ", ") + algorithm;
            }
            getLogger().info(" signalgorithm is on of " + availableSignAlgs);
            getLogger()
                    .info(" adding the parameters '-certprofile profileName' makes the CA use the certificate profile 'profileName' instead of the default ROOTCA or SUBCA. Optional parameter that can be completely left out.");
            getLogger()
                    .info(" adding the parameters '-superadmincn SuperAdmin' makes an initial CA use the common name SuperAdmin and initialize the authorization module with an initial super administrator. Note only used when creating initial CA. If parameter is not given, the authorization rules are untouched.");
            getLogger()
                    .info(" catokenproperties is a file were you define key name, password and key alias for the HSM. Same as the Hard CA Token Properties in admin gui.");
            getLogger()
                    .info(" signed by caid is the CA id of a CA that will sign this CA. If this is omitted the new CA will be self signed (i.e. a root CA).");
            return;
        }

        try {
            // Get and remove optional switches
            List<String> argsList = CliTools.getAsModifyableList(args);
            int profileInd = argsList.indexOf("-certprofile");
            String profileName = null;
            if (profileInd > -1) {
                profileName = argsList.get(profileInd + 1);
                argsList.remove(profileInd + 1);
                argsList.remove("-certprofile");
            }
            int superAdminCNInd = argsList.indexOf("-superadmincn");
            String superAdminCN = null;
            if (superAdminCNInd > -1) {
                superAdminCN = argsList.get(superAdminCNInd + 1);
                argsList.remove(superAdminCNInd + 1);
                argsList.remove("-superadmincn");
            }
            int typeIndex = argsList.indexOf("-type");
            //Default is X509
            CaType type = CaType.X509;
            if (typeIndex > -1) {
                String typeName = argsList.get(typeIndex + 1);
                type = CaType.lookupCaType(typeName);
                if (type == null) {
                    throw new InvalidParameterException("CA type of name " + typeName + " unknown. Available types: " + CaType.getTypeNames());
                }
                argsList.remove(typeIndex + 1);
                argsList.remove("-type");
            }

            args = argsList.toArray(new String[argsList.size()]); // new args array without the optional switches

            final String caname = args[1];
            final String dn = CertTools.stringToBCDNString(StringTools.strip(args[2]));
            final String catokentype = args[3];
            String catokenpassword = StringTools.passwordDecryption(args[4], "ca.tokenpassword");
            if (StringUtils.equals(catokenpassword, "prompt")) {
                getLogger().info("Enter CA token password: ");
                getLogger().info("");
                catokenpassword = String.valueOf(System.console().readPassword());
            }
            final String keyspec = args[5];
            final String keytype = args[6];
            final int validity = Integer.parseInt(args[7]);
            String policyId = args[8];
            final ArrayList<CertificatePolicy> policies = new ArrayList<CertificatePolicy>(1);
            if ((policyId != null) && (policyId.toLowerCase().trim().equals("null"))) {
                policyId = null;
            } else {
                String[] array = policyId.split(" ");
                for (int i = 0; i < array.length; i += 2) {
                    String id = array[i + 0];
                    String cpsurl = "";
                    if (array.length > i + 1) {
                        cpsurl = array[i + 1];
                    }
                    policies.add(new CertificatePolicy(id, CertificatePolicy.id_qt_cps, cpsurl));
                }
            }
            String signAlg = args[9];
            String catokenproperties = null;
            if (args.length > 10 && !"soft".equals(catokentype)) {
                String filename = args[10];
                if ((filename != null) && (!filename.equalsIgnoreCase("null"))) {
                    if (!(new File(filename)).exists()) {
                        throw new IllegalAdminCommandException("File " + filename + " does not exist");
                    }
                    catokenproperties = new String(FileTools.readFiletoBuffer(filename));
                }
            }
            int signedByCAId = CAInfo.SELFSIGNED;
            if (args.length > 11) {
                String caid = args[11];
                signedByCAId = Integer.valueOf(caid);
            }
            // Get the profile ID from the name if we specified a certain profile name
            int certificateProfileId = CertificateProfileConstants.CERTPROFILE_FIXED_ROOTCA;
            if (profileName == null) {
                if (signedByCAId == CAInfo.SELFSIGNED) {
                    profileName = "ROOTCA";
                } else {
                    profileName = "SUBCA";
                    certificateProfileId = CertificateProfileConstants.CERTPROFILE_FIXED_SUBCA;
                }
            } else {
                certificateProfileId = ejb.getRemoteSession(CertificateProfileSessionRemote.class).getCertificateProfileId(profileName);
                if (certificateProfileId == 0) {
                    getLogger().info("Error: Certificate profile with name '" + profileName + "' does not exist.");
                    return;
                }

                CertificateProfile certificateProfile = ejb.getRemoteSession(CertificateProfileSessionRemote.class)
                        .getCertificateProfile(profileName);
                if (certificateProfile.getType() != CertificateConstants.CERTTYPE_ROOTCA
                        && certificateProfile.getType() != CertificateConstants.CERTTYPE_SUBCA) {
                    getLogger().info("Error: Certificate profile " + profileName + " is not of type ROOTCA or SUBCA.");
                    return;
                }
            }

            if (KeyTools.isUsingExportableCryptography()) {
                getLogger().warn("WARNING!");
                getLogger().warn("WARNING: Using exportable strength crypto!");
                getLogger().warn("WARNING!");
                getLogger()
                        .warn("The Unlimited Strength Crypto policy files have not been installed. EJBCA may not function correctly using exportable crypto.");
                getLogger().warn("Please install the Unlimited Strength Crypto policy files as documented in the Installation guide.");
                getLogger().warn("Sleeping 10 seconds...");
                getLogger().warn("");
                Thread.sleep(10000);
            }
            getLogger().info("Initializing CA");

            getLogger().info("Generating rootCA keystore:");
            getLogger().info("CA Type:" + type.getTypeName());
            getLogger().info("CA name: " + caname);
            getLogger().info("SuperAdmin CN: " + superAdminCN);
            getLogger().info("DN: " + dn);
            getLogger().info("CA token type: " + catokentype);
            getLogger().info("CA token password: " + (catokenpassword == null ? "null" : "hidden"));
            getLogger().info("Keytype: " + keytype);
            getLogger().info("Keyspec: " + keyspec);
            getLogger().info("Validity (days): " + validity);
            getLogger().info("Policy ID: " + policyId);
            getLogger().info("Signature alg: " + signAlg);
            getLogger().info("Certificate profile: " + profileName);
            //getLogger().info("Certificate profile id: "+profileId);
            getLogger().info("CA token properties: " + catokenproperties);
            getLogger().info("Signed by: " + (signedByCAId == CAInfo.SELFSIGNED ? "self signed " : signedByCAId));
            if (signedByCAId != CAInfo.SELFSIGNED) {
                try {
                    EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class).getCAInfo(getAdmin(cliUserName, cliPassword), signedByCAId);
                } catch (CADoesntExistsException e) {
                    throw new IllegalArgumentException("CA with id " + signedByCAId + " does not exist.");
                }
            }

            if (superAdminCN != null) {
                initAuthorizationModule(getAdmin(cliUserName, cliPassword), dn.hashCode(), superAdminCN);
            }
            // Define CAToken type (soft token or hsm).
            CATokenInfo catokeninfo = new CATokenInfo();
            catokeninfo.setSignatureAlgorithm(signAlg);
            catokeninfo.setEncryptionAlgorithm(AlgorithmConstants.SIGALG_SHA1_WITH_RSA);
            catokeninfo.setProperties(catokenproperties);
            Properties prop = catokeninfo.getProperties();
            // Set some CA token properties if they are not set already
            if (prop.getProperty(CryptoToken.KEYSPEC_PROPERTY) == null) {
                prop.setProperty(CryptoToken.KEYSPEC_PROPERTY, keyspec);
            }
            if (prop.getProperty(CATokenConstants.CAKEYPURPOSE_CERTSIGN_STRING) == null) {
                prop.setProperty(CATokenConstants.CAKEYPURPOSE_CERTSIGN_STRING, CAToken.SOFTPRIVATESIGNKEYALIAS);
            }
            if (prop.getProperty(CATokenConstants.CAKEYPURPOSE_CRLSIGN_STRING) == null) {
                prop.setProperty(CATokenConstants.CAKEYPURPOSE_CRLSIGN_STRING, CAToken.SOFTPRIVATESIGNKEYALIAS);
            }
            if (prop.getProperty(CATokenConstants.CAKEYPURPOSE_DEFAULT_STRING) == null) {
                prop.setProperty(CATokenConstants.CAKEYPURPOSE_DEFAULT_STRING, CAToken.SOFTPRIVATEDECKEYALIAS);
            }
            if (!catokenpassword.equalsIgnoreCase("null")) {
                catokeninfo.setAuthenticationCode(catokenpassword);
            }
            if ( catokentype.equalsIgnoreCase("soft")) {
                catokeninfo.setClassPath(SoftCryptoToken.class.getName());
                if (catokeninfo.getAuthenticationCode() != null) {
                    // We must do this in order to not set the default password when creating a new soft CA token
                    // A bit tricky, but thats how it is as of EJBCA 5.0.x, 2012-05.
                    getLogger().info("Non default password used for soft CA token, auto activation disabled.");
                    prop.setProperty(SoftCryptoToken.NODEFAULTPWD, "true");
                }
            } else {
                catokeninfo.setClassPath(catokentype);
            }
            catokeninfo.setProperties(prop);

            CAInfo cainfo = null;
            switch (type) {
            case CVC:
                // Get keysequence from SERIALNUMBER in DN is it exists
                final String keysequence = CertTools.getPartFromDN(dn, "SN");
                if (keysequence != null) {
                    getLogger().info("CVC key sequence: "+keysequence);
                    catokeninfo.setKeySequence(keysequence);
                    if (StringUtils.isNumeric(keysequence)) {
                        getLogger().info("CVC key sequence format is numeric.");
                        catokeninfo.setKeySequenceFormat(StringTools.KEY_SEQUENCE_FORMAT_NUMERIC);
                    } else {
                        getLogger().info("CVC key sequence format is alphanumeric.");
                        catokeninfo.setKeySequenceFormat(StringTools.KEY_SEQUENCE_FORMAT_ALPHANUMERIC);
                    }
                }
                cainfo = createCVCCAInfo(dn, caname, certificateProfileId, validity, signedByCAId, catokeninfo);
                break;
            case X509:
                //Default, slip below.
            default:
                // Create and active OSCP CA Service.
                ArrayList<ExtendedCAServiceInfo> extendedcaservices = new ArrayList<ExtendedCAServiceInfo>();
                String extendedServiceKeySpec = keyspec;
                if (keytype.equals(AlgorithmConstants.KEYALGORITHM_RSA)) {
                    // Never use larger keys than 2048 bit RSA for OCSP signing
                    int len = Integer.parseInt(extendedServiceKeySpec);
                    if (len > 2048) {
                        extendedServiceKeySpec = "2048";
                    }
                }
                extendedcaservices.add(new OCSPCAServiceInfo(ExtendedCAServiceInfo.STATUS_ACTIVE));
                extendedcaservices.add(new XKMSCAServiceInfo(ExtendedCAServiceInfo.STATUS_INACTIVE, "CN=XKMSCertificate, " + dn, "",
                        extendedServiceKeySpec, keytype));
                extendedcaservices.add(new CmsCAServiceInfo(ExtendedCAServiceInfo.STATUS_INACTIVE, "CN=CmsCertificate, " + dn, "",
                        extendedServiceKeySpec, keytype));
                extendedcaservices.add(new HardTokenEncryptCAServiceInfo(ExtendedCAServiceInfo.STATUS_ACTIVE));
                extendedcaservices.add(new KeyRecoveryCAServiceInfo(ExtendedCAServiceInfo.STATUS_ACTIVE));
                cainfo = createX509CaInfo(dn, caname, certificateProfileId, validity, signedByCAId, catokeninfo, policies, extendedcaservices);
                break;
            }
            getLogger().info("Creating CA...");
            ejb.getRemoteSession(CAAdminSessionRemote.class).createCA(getAdmin(cliUserName, cliPassword), cainfo);

            CAInfo newInfo = ejb.getRemoteSession(CaSessionRemote.class).getCAInfo(getAdmin(cliUserName, cliPassword), caname);
            int caid = newInfo.getCAId();
            getLogger().info("CAId for created CA: " + caid);
            getLogger().info("Created and published initial CRL.");
            getLogger().info("CA initialized");
            getLogger().info("Note that any open browser sessions must be restarted to interact with this CA.");
        } catch (Exception e) {
            getLogger().debug("An error occured: ", e);
            throw new ErrorAdminCommandException(e);
        }
    }

    private CAInfo createX509CaInfo(String dn, String caname, int certificateProfileId, long validity, int signedByCAId, CATokenInfo catokeninfo,
            ArrayList<CertificatePolicy> policies, ArrayList<ExtendedCAServiceInfo> extendedcaservices) {
        return new X509CAInfo(dn, caname, CAConstants.CA_ACTIVE, new Date(), "", certificateProfileId, validity, null, // Expiretime                                             
                CAInfo.CATYPE_X509, signedByCAId, new ArrayList<Certificate>(), // empty certificate chain
                catokeninfo, "Initial CA", -1, null, policies, // PolicyId
                24 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLPeriod
                0 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLIssueInterval
                10 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLOverlapTime
                0 * SimpleTime.MILLISECONDS_PER_HOUR, // DeltaCRLPeriod
                new ArrayList<Integer>(), true, // Authority Key Identifier
                false, // Authority Key Identifier Critical
                true, // CRL Number
                false, // CRL Number Critical
                "", // Default CRL Dist Point
                "", // Default CRL Issuer
                "", // Default OCSP Service Locator
                null, // Authority Information Access
                "", // CA defined freshest CRL
                true, // Finish User
                extendedcaservices, false, // use default utf8 settings
                new ArrayList<Integer>(), // Approvals Settings
                1, // Number of Req approvals
                false, // Use UTF8 subject DN by default
                true, // Use LDAP DN order by default
                false, // Use CRL Distribution Point on CRL
                false, // CRL Distribution Point on CRL critical
                true, // include in health check
                true, // isDoEnforceUniquePublicKeys
                true, // isDoEnforceUniqueDistinguishedName
                false, // isDoEnforceUniqueSubjectDNSerialnumber
                true, // useCertReqHistory
                true, // useUserStorage
                true, // useCertificateStorage
                null //cmpRaAuthSecret
        );
    }
    
    private CAInfo createCVCCAInfo(String dn, String caname, int certificateProfileId, long validity, int signedByCa, CATokenInfo catokeninfo) {
        return new CVCCAInfo(dn, caname, CAConstants.CA_ACTIVE, new Date(), certificateProfileId, validity, 
                null, // Expiretime
                CAInfo.CATYPE_CVC, signedByCa, new ArrayList<Certificate>(), catokeninfo, "Initial CA", -1, null, 
                24 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLPeriod
                0 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLIssueInterval
                10 * SimpleTime.MILLISECONDS_PER_HOUR, // CRLOverlapTime
                0 * SimpleTime.MILLISECONDS_PER_HOUR, // DeltaCRLPeriod
                new ArrayList<Integer>(), // CRL publishers
                true, // Finish User
                new ArrayList<ExtendedCAServiceInfo>(), // extendedcaservices, 
                new ArrayList<Integer>(), // Approvals Settings
                1, // Number of Req approvals
                true, // Include in health check
                true, // isDoEnforceUniquePublicKeys
                true, // isDoEnforceUniqueDistinguishedName
                false, // isDoEnforceUniqueSubjectDNSerialnumber
                true, // useCertReqHistory
                true, // useUserStorage
                true // useCertificateStorage
        );
    }

}
