/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package de.bayen.freibier.process;

import java.util.logging.Level;

import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.MAcctSchema;
import org.compiere.model.MAcctSchemaElement;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceBatch;
import org.compiere.model.MInvoiceBatchLine;
import org.compiere.model.MInvoiceLine;
import org.compiere.model.MSalesRegion;
import org.compiere.model.X_C_AcctSchema_Element;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.AdempiereUserError;
import org.compiere.util.Env;
import org.compiere.util.Msg;


/**
 * Process Invoice Batch
 * 
 * This class is a changed version from
 * {@link org.compiere.process.InvoiceBatchProcess} from Jorg Janke. It works
 * better with the slightly changed data structure of FreiBier.
 * 
 * @author Jorg Janke
 * @version $Id: InvoiceBatchProcess.java,v 1.2 2006/07/30 00:51:01 jjanke Exp $
 */
public class InvoiceBatchProcess extends SvrProcess
{
	/**	Batch to process		*/
	private int		p_C_InvoiceBatch_ID = 0;
	/** Action					*/
	private String	p_DocAction = null;
	
	/** Invoice					*/
	private MInvoice	m_invoice = null;
	/** Old DocumentNo			*/
	private String		m_oldDocumentNo = null;
	/** Old BPartner			*/
	private int			m_oldC_BPartner_ID = 0;
	/** Old BPartner Loc		*/
	private int			m_oldC_BPartner_Location_ID = 0;
	
	/** Counter					*/
	private int			m_count = 0;
	
	/**
	 *  Prepare - get Parameters.
	 */
	protected void prepare()
	{
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++)
		{
			String name = para[i].getParameterName();
			if (para[i].getParameter() == null)
				;
			else if (name.equals("DocAction"))
				p_DocAction = (String)para[i].getParameter();
		}
		p_C_InvoiceBatch_ID = getRecord_ID();
	}   //  prepare

	/**
	 * 	Process Invoice Batch
	 *	@return message
	 *	@throws Exception
	 */
	protected String doIt () throws Exception
	{
		if (log.isLoggable(Level.INFO)) log.info("C_InvoiceBatch_ID=" + p_C_InvoiceBatch_ID + ", DocAction=" + p_DocAction);
		if (p_C_InvoiceBatch_ID == 0)
			throw new AdempiereUserError("C_InvoiceBatch_ID = 0");
		MInvoiceBatch batch = new MInvoiceBatch(getCtx(), p_C_InvoiceBatch_ID, get_TrxName());
		if (batch.get_ID() == 0)
			throw new AdempiereUserError("@NotFound@: @C_InvoiceBatch_ID@ - " + p_C_InvoiceBatch_ID);
		if (batch.isProcessed())
			throw new AdempiereUserError("@Processed@");
		//
		if (batch.getControlAmt().signum() != 0
			&& batch.getControlAmt().compareTo(batch.getDocumentAmt()) != 0)
			throw new AdempiereUserError("@ControlAmt@ <> @DocumentAmt@");		
		//
		MInvoiceBatchLine[] lines = batch.getLines(false);
		for (int i = 0; i < lines.length; i++)
		{
			MInvoiceBatchLine line = lines[i];
			if (line.getC_Invoice_ID() != 0 || line.getC_InvoiceLine_ID() != 0)
				continue;
			
			/*
			 * Die folgende Abfrage habe ich auskommentiert, weil wir aus jeder
			 * einzelnen Stapelzeile eine einzelne Rechnung haben wollen. Im
			 * ursprünglichen Code wurden Zeilen des gleichen GEschäftspartners
			 * in einem Beleg zusamemngefasst.
			 */
//			if ((m_oldDocumentNo != null 
//					&& !m_oldDocumentNo.equals(line.getDocumentNo()))
//				|| m_oldC_BPartner_ID != line.getC_BPartner_ID()
//				|| m_oldC_BPartner_Location_ID != line.getC_BPartner_Location_ID())
			completeInvoice();
			//	New Invoice
			if (m_invoice == null)
			{
				m_invoice = new MInvoice (batch, line);
				{
					/*
					 * changes for FreiBier -TB-
					 * 
					 * these changes are implemented in an FreiBier-independent
					 * way. They could be included in trunk. If you want to do
					 * that you should decide if the thrown Exceptions should be
					 * thrown or suppressed.
					 */
					
					if ("Y".equals(Env.getContext(getCtx(), "$Element_SR"))) {
						// Sales Region Reference
						String sr_colName = MSalesRegion.COLUMNNAME_C_SalesRegion_ID;
						Object salesRegion = line.get_Value(sr_colName);
						if (!m_invoice.set_ValueOfColumnReturningBoolean(
								sr_colName, salesRegion)) {
							throw new AdempiereException(
									"Can not write SalesRegion to Invoice. Is this database adapted to FreiBier?");
						}
					}
					//
					if ("Y".equals(Env.getContext(getCtx(), "$Element_X1"))) {
						// User Defined Reference.
						// In FreiBier used for BAY_Contract
						MAcctSchema[] ass = MAcctSchema.getClientAcctSchema(getCtx(), getAD_Client_ID());
						if (ass.length != 1)
							throw new AdempiereException(
									"Not implemented for more than one Accounting Schema.");
						MAcctSchemaElement ud1 = ass[0]
								.getAcctSchemaElement(X_C_AcctSchema_Element.ELEMENTTYPE_UserColumn1);
						String userElement1ColumnName;
						if (ud1 != null) {
							userElement1ColumnName = ud1.getDisplayColumnName();
							Object userElement = line
									.get_Value(userElement1ColumnName);
							if (!m_invoice.set_ValueOfColumnReturningBoolean(
									userElement1ColumnName, userElement)) {
								throw new AdempiereException(
										"Can not write UserElement1 to Invoice. Is this database adapted to FreiBier?");
							}
						}
					}
					/*
					 * Ich möchte die Beschreibung der Zeile an der Rechnung
					 * haben und nicht die des Batches. Das sollte deshalb kein
					 * Problem sein, weil ich weiter oben dafür gesorgt habe,
					 * das keine Belege mit mehreren Zeilen erzeugt werden.
					 */
					m_invoice.setDescription(line.getDescription());
				}
				if (!m_invoice.save())
					throw new AdempiereUserError("Cannot save Invoice: "+line.getDocumentNo());
				//
				m_oldDocumentNo = line.getDocumentNo();
				m_oldC_BPartner_ID = line.getC_BPartner_ID();
				m_oldC_BPartner_Location_ID = line.getC_BPartner_Location_ID();
			}
			
			if (line.isTaxIncluded() != m_invoice.isTaxIncluded())
			{
				//	rollback
				throw new AdempiereUserError("Line " + line.getLine() + " TaxIncluded inconsistent");
			}
			
			//	Add Line
			MInvoiceLine invoiceLine = new MInvoiceLine (m_invoice);
			invoiceLine.setDescription(line.getDescription());
			invoiceLine.setC_Charge_ID(line.getC_Charge_ID());
			invoiceLine.setQty(line.getQtyEntered());	// Entered/Invoiced
			invoiceLine.setPrice(line.getPriceEntered());
			invoiceLine.setC_Tax_ID(line.getC_Tax_ID());
			invoiceLine.setTaxAmt(line.getTaxAmt());
			invoiceLine.setLineNetAmt(line.getLineNetAmt());
			invoiceLine.setLineTotalAmt(line.getLineTotalAmt());
			if (!invoiceLine.save())
			{
				//	rollback
				throw new AdempiereUserError("Cannot save Invoice Line");
			}

			//	Update Batch Line
			line.setC_Invoice_ID(m_invoice.getC_Invoice_ID());
			line.setC_InvoiceLine_ID(invoiceLine.getC_InvoiceLine_ID());
			line.saveEx();
			
		}	//	for all lines
		completeInvoice();
		//
		batch.setProcessed(true);
		batch.saveEx();
		
		StringBuilder msgreturn = new StringBuilder("#").append(m_count);
		return msgreturn.toString();
	}	//	doIt
	
	
	/**
	 * 	Complete Invoice
	 */
	private void completeInvoice()
	{
		if (m_invoice == null)
			return;
		
		m_invoice.setDocAction(p_DocAction);
		if(!m_invoice.processIt(p_DocAction)) {
			log.warning("Invoice Process Failed: " + m_invoice + " - " + m_invoice.getProcessMsg());
			throw new IllegalStateException("Invoice Process Failed: " + m_invoice + " - " + m_invoice.getProcessMsg());
			
		}
		m_invoice.saveEx();
		
		String message = Msg.parseTranslation(getCtx(), "@InvoiceProcessed@ " + m_invoice.getDocumentNo());
		addBufferLog(0, m_invoice.getDateInvoiced(), m_invoice.getGrandTotal(), message, m_invoice.get_Table_ID(), m_invoice.getC_Invoice_ID());
		m_count++;
		
		m_invoice = null;
	}	//	completeInvoice
	
}	//	InvoiceBatchProcess
