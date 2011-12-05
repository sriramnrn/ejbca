
package org.ejbca.core.protocol.ws.client.gen;

import javax.xml.ws.WebFault;


/**
 * This class was generated by the JAXWS SI.
 * JAX-WS RI 2.0_01-b59-fcs
 * Generated source version: 2.0
 * 
 */
@WebFault(name = "ApprovalRequestExecutionException", targetNamespace = "http://ws.protocol.core.ejbca.org/")
public class ApprovalRequestExecutionException_Exception extends Exception {

    private static final long serialVersionUID = 8322149806693093967L;
    /**
     * Java type that goes as soapenv:Fault detail element.
     * 
     */
    private ApprovalRequestExecutionException faultInfo;

    /**
     * 
     * @param message
     * @param faultInfo
     */
    public ApprovalRequestExecutionException_Exception(String message, ApprovalRequestExecutionException faultInfo) {
        super(message);
        this.faultInfo = faultInfo;
    }

    /**
     * 
     * @param message
     * @param faultInfo
     * @param cause
     */
    public ApprovalRequestExecutionException_Exception(String message, ApprovalRequestExecutionException faultInfo, Throwable cause) {
        super(message, cause);
        this.faultInfo = faultInfo;
    }

    /**
     * 
     * @return
     *     returns fault bean: org.ejbca.core.protocol.ws.client.gen.ApprovalRequestExecutionException
     */
    public ApprovalRequestExecutionException getFaultInfo() {
        return faultInfo;
    }

}
