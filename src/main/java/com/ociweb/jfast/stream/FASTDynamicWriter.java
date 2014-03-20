package com.ociweb.jfast.stream;

import com.ociweb.jfast.field.TokenBuilder;
import com.ociweb.jfast.field.TypeMask;
import com.ociweb.jfast.loader.TemplateCatalog;
import com.ociweb.jfast.primitive.PrimitiveWriter;

public class FASTDynamicWriter {

	private final FASTWriterDispatch writerDispatch;
	private final TemplateCatalog catalog;
	private final int[] fullScript;
	private final FASTRingBuffer ringBuffer;
	
	private int activeScriptCursor;
	private int activeScriptLimit;
	
	public FASTDynamicWriter(PrimitiveWriter primitiveWriter, TemplateCatalog catalog, FASTRingBuffer ringBuffer) {

		this.writerDispatch = new FASTWriterDispatch(primitiveWriter,
										catalog.dictionaryFactory(),
										catalog.templatesCount(), 
										catalog.getMaxTextLength(), catalog.getMaxByteVectorLength(), 
										catalog.getTextGap(), catalog.getByteVectorGap(),
										ringBuffer);
		this.catalog = catalog;
		this.fullScript = catalog.fullScript();
		this.ringBuffer = ringBuffer;
	}

	public void write() {
				
		
		//	random access to fields is supported in the ring buffer however the dynamic writer
	    //  requires the queue to have all the fields in the right order to speed encoding.
		//  Once a message/sequence is written the queue position is moved forward.
		
		
		//avalaible for read check
//		if (ringBuffer.isBlocked(1)) {
//			//TODO: what to do if can not read next?
//			return;//try again later
//		};
		
		//RingBuffer rules
		//Writer will not release the templateId unless all the fields are also released up to sequence or end.
		//Each sequence is released in full. 
		//As a result reader only needs to check for overrun in those two cases.
		
		
		
		//TODO: open the group.
		int idx = 0;
		int templateId = ringBuffer.readInteger(idx); 				
		//tokens - reading 
		activeScriptCursor = catalog.getTemplateStartIdx(templateId);
		activeScriptLimit = catalog.getTemplateLimitIdx(templateId);
		
		do {
			int token = fullScript[activeScriptCursor];			
			
			int tokenType = TokenBuilder.extractType(token);
			if (TypeMask.GroupLength==tokenType) {
				//TODO: loop over this length for each sequence.
				
				
			}
			
			
			writerDispatch.dispatchWriteByToken(token,idx);		
		} while (++activeScriptCursor<activeScriptLimit);
		//TODO: close the group
		
		
		//ringBuffer.removeForward(step);
		////
		//Hack until the move forward is called.
		ringBuffer.dump(); //must dump values in buffer or we will hang when reading.
		
		
	}

}
