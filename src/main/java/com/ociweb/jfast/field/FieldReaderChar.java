//Copyright 2013, Nathan Tippy
//See LICENSE file for BSD license details.
//Send support requests to http://www.ociweb.com/contact
package com.ociweb.jfast.field;

import com.ociweb.jfast.loader.DictionaryFactory;
import com.ociweb.jfast.primitive.PrimitiveReader;

public class FieldReaderChar {

	private static final int INIT_VALUE_MASK = 0x80000000;
	private final PrimitiveReader reader;
	private final TextHeap charDictionary;
	private final int INSTANCE_MASK;
	
	public FieldReaderChar(PrimitiveReader reader, TextHeap charDictionary) {
		
		assert(null==charDictionary || charDictionary.itemCount()<TokenBuilder.MAX_INSTANCE);
		assert(null==charDictionary || FieldReaderInteger.isPowerOfTwo(charDictionary.itemCount()));
		
		this.INSTANCE_MASK = null==charDictionary ? 0 : Math.min(TokenBuilder.MAX_INSTANCE, (charDictionary.itemCount()-1));
		
		this.reader = reader;
		this.charDictionary = charDictionary;
	}
	
	public TextHeap textHeap() {
		return charDictionary;
	}
	
	public void reset() {
		if (null!=charDictionary) {
			charDictionary.reset();		
		}
	}
	
	static boolean isPowerOfTwo(int length) {
		
		while (0==(length&1)) {
			length = length>>1;
		}
		return length==1;
	}

	final byte NULL_STOP = (byte)0x80;

	public int readASCIICopy(int token, int readFromIdx) {
		int idx = token & INSTANCE_MASK;
		
		if (reader.popPMapBit()!=0) {
			byte val = reader.readTextASCIIByte();
			if (0!=(val&0x7F)) {
				//real data, this is the most common case;
				charDictionary.setZeroLength(idx);				
				fastHeapAppend(idx, val);
			} else {
				readASCIIToHeapNone(idx, val);
			}
		}
		return idx;
	}

	
	private void readASCIIToHeapNone(int idx, byte val) {
		// 0x80 is a null string.
		// 0x00, 0x80 is zero length string
		if (0==val) {
			//almost never happens
			charDictionary.setZeroLength(idx);
			//must move cursor off the second byte
			val = reader.readTextASCIIByte(); //< .1%
			//at least do a validation because we already have what we need
			assert((val&0xFF)==0x80);			
		} else {
			//happens rarely when it equals 0x80
			charDictionary.setNull(idx);				
			
		}
	}

	private void fastHeapAppend(int idx, byte val) {
		int offset = idx<<2;
		int nextLimit = charDictionary.tat[offset+4];
		int targIndex = charDictionary.tat[offset+1];
		
		char[] targ = charDictionary.rawAccess();
				
		if (targIndex>nextLimit) {
			charDictionary.makeSpaceForAppend(offset, 2); //also space for last char
			nextLimit = charDictionary.tat[offset+4];
		}
		
		if(val>=0) {
			targ[targIndex++] = (char)val;			
		
			int len;
			do {
				len = reader.readTextASCII2(targ, targIndex, nextLimit);
				if (len<0) {
					targIndex-=len;
					System.err.println("NOW DELETE THIS,  tested make space:"+offset);
					charDictionary.makeSpaceForAppend(offset, 2); //also space for last char
					nextLimit = charDictionary.tat[offset+4];
				} else {
					targIndex+=len;
				}
			} while (len<0);
		} else {
			targ[targIndex++] = (char)(0x7F & val);
		}
		int stopIndex = charDictionary.tat[offset+1] = targIndex;
	}
	
	
	public int readASCIIConstant(int token, int readFromIdx) {
		//always return this required value.
		return token & INSTANCE_MASK;
	}
	
	public int readASCIIConstantOptional(int token, int readFromIdx) {
		return (reader.popPMapBit()==0 ? (token & INSTANCE_MASK)|INIT_VALUE_MASK : token & INSTANCE_MASK);
	}

	public int readUTF8Constant(int token, int readFromIdx) {
		//always return this required value.
		return token & INSTANCE_MASK;
	}
	
	public int readUTF8ConstantOptional(int token, int readFromIdx) {
		return (reader.popPMapBit()==0 ? (token & INSTANCE_MASK)|INIT_VALUE_MASK : token & INSTANCE_MASK);
	}
	
	public int readASCIIDefault(int token, int readFromIdx) {
		int idx = token & INSTANCE_MASK;
		
		if (reader.popPMapBit()==0) {
			return idx|INIT_VALUE_MASK;//use constant
		} else {
			byte val = reader.readTextASCIIByte();
			if (0!=(val&0x7F)) {
				//real data, this is the most common case;
				charDictionary.setZeroLength(idx);				
				fastHeapAppend(idx, val);
			} else {
				readASCIIToHeapNone(idx, val);
			}
			return idx;
		}
	}
	
	public int readASCIIDefaultOptional(int token, int readFromIdx) {
		//for ASCII we don't need special behavior for optional
		return readASCIIDefault(token, readFromIdx); 
	}

	public int readASCIIDeltaOptional(int token, int readFromIdx) {
		return readASCIIDelta(token, readFromIdx);//TODO: need null logic here.
	}

	public int readASCIIDelta(int token, int readFromIdx) {
		int idx = token & INSTANCE_MASK;
		
		int trim = reader.readIntegerSigned();
		
		if (trim>=0) {
			return readASCIITail(idx, trim, readFromIdx);
		} else {
			return readASCIIHead(idx, trim, readFromIdx);
		}
	}

	public int readASCIITail(int token, int readFromIdx) {
		return readASCIITail(token & INSTANCE_MASK, reader.readIntegerUnsigned(), readFromIdx);
	}

	private int readASCIITail(int idx, int trim, int readFromIdx) {
		if (trim>0) {
			charDictionary.trimTail(idx, trim);
		}
		
		//System.err.println("read: trim "+trim);
		
		byte val = reader.readTextASCIIByte();
		if (val==0) {
			//nothing to append
			//must move cursor off the second byte
			val = reader.readTextASCIIByte();
			//at least do a validation because we already have what we need
			assert((val&0xFF)==0x80);
		} else {
			if (val==NULL_STOP) {
				//nothing to append and sent value is null
				charDictionary.setNull(idx);				
			} else {		
				if (charDictionary.isNull(idx)) {
					charDictionary.setZeroLength(idx);
				}
				fastHeapAppend(idx, val);
			}
		}
		
		return idx;
	}
	
	public int readASCIITailOptional(int token, int readFromIdx) {
		int idx = token & INSTANCE_MASK;
				
		int tail = reader.readIntegerUnsigned();
		if (0==tail) {
			charDictionary.setNull(idx);
			return idx;
		}
		tail--;
				
		charDictionary.trimTail(idx, tail);
		byte val = reader.readTextASCIIByte();
		if (val==0) {
			//nothing to append
			//must move cursor off the second byte
			val = reader.readTextASCIIByte();
			//at least do a validation because we already have what we need
			assert((val&0xFF)==0x80);
		} else {
			if (val==NULL_STOP) {
				//nothing to append
				//charDictionary.setNull(idx);				
			} else {		
				if (charDictionary.isNull(idx)) {
					charDictionary.setZeroLength(idx);
				}
				fastHeapAppend(idx, val);
			}
		}
						
		return idx;
	}
	
	private int readASCIIHead(int idx, int trim, int readFromIdx) {
		if (trim<0) {
			charDictionary.trimHead(idx, -trim);
		}

		byte value = reader.readTextASCIIByte();
		int offset = idx<<2;
		int nextLimit = charDictionary.tat[offset+4];
		
		if (trim>=0) {
			while (value>=0) {
				nextLimit = charDictionary.appendTail(offset, nextLimit, (char)value);
				value = reader.readTextASCIIByte();
			}
			charDictionary.appendTail(offset, nextLimit, (char)(value&0x7F) );
		} else {
			while (value>=0) {
				charDictionary.appendHead(offset, (char)value);
				value = reader.readTextASCIIByte();
			}
			charDictionary.appendHead(offset, (char)(value&0x7F) );
		}
		
	//	System.out.println("new ASCII string:"+charDictionary.get(idx, new StringBuilder()));
		
		return idx;
	}


	public int readASCIICopyOptional(int token, int readFromIdx) {
		int idx = token & INSTANCE_MASK;
		
		if (reader.popPMapBit()!=0) {
			byte val = reader.readTextASCIIByte();
			if (0!=(val&0x7F)) {
				//real data, this is the most common case;
				charDictionary.setZeroLength(idx);				
				fastHeapAppend(idx, val);
			} else {
				readASCIIToHeapNone(idx, val);
			}
		}
		return idx;
	}





	public int readUTF8Default(int token, int readFromIdx) {
		int idx = token & INSTANCE_MASK;
		
		if (reader.popPMapBit()==0) {
			return idx|INIT_VALUE_MASK;//use constant
		} else {
			
			int length = reader.readIntegerUnsigned();
			reader.readTextUTF8(charDictionary.rawAccess(), 
					            charDictionary.allocate(idx, length),
					            length);
						
			return idx;
		}
	}
	

	public int readUTF8DefaultOptional(int token, int readFromIdx) {
		int idx = token & INSTANCE_MASK;
		
		if (reader.popPMapBit()==0) {
			return idx|INIT_VALUE_MASK;//use constant
		} else {
			
			int length = reader.readIntegerUnsigned()-1;
			reader.readTextUTF8(charDictionary.rawAccess(), 
					            charDictionary.allocate(idx, length),
					            length);
						
			return idx;
		}
	}

	public int readUTF8Delta(int token, int readFromIdx) {
		int idx = token & INSTANCE_MASK;
		
		int trim = reader.readIntegerSigned();
		int utfLength = reader.readIntegerUnsigned();
		if (trim>=0) {
			//append to tail
			reader.readTextUTF8(charDictionary.rawAccess(), charDictionary.makeSpaceForAppend(idx, trim, utfLength), utfLength);
		} else {
			//append to head
			reader.readTextUTF8(charDictionary.rawAccess(), charDictionary.makeSpaceForPrepend(idx, -trim, utfLength), utfLength);
		//	System.out.println("new UTF8 string:"+charDictionary.get(idx, new StringBuilder()));
		}
		
		return idx;
	}

	public int readUTF8Tail(int token, int readFromIdx) {
		int idx = token & INSTANCE_MASK;
		
		int trim = reader.readIntegerSigned();
		int utfLength = reader.readIntegerUnsigned(); 

		//append to tail	
		int targetOffset = charDictionary.makeSpaceForAppend(idx, trim, utfLength);
		reader.readTextUTF8(charDictionary.rawAccess(), targetOffset, utfLength);
		return idx;
	}
	
	public int readUTF8Copy(int token, int readFromIdx) {
		int idx = token & INSTANCE_MASK;
		if (reader.popPMapBit()!=0) {
			int length = reader.readIntegerUnsigned();
			reader.readTextUTF8(charDictionary.rawAccess(), 
					            charDictionary.allocate(idx, length),
					            length);
		}
		return idx;
	}

	public int readUTF8CopyOptional(int token, int readFromIdx) {
		int idx = token & INSTANCE_MASK;
		if (reader.popPMapBit()!=0) {			
			int length = reader.readIntegerUnsigned()-1;
			reader.readTextUTF8(charDictionary.rawAccess(), 
					            charDictionary.allocate(idx, length),
					            length);
		}
		return idx;
	}


	public int readUTF8DeltaOptional(int token, int readFromIdx) {
		int idx = token & INSTANCE_MASK;
		
		int trim = reader.readIntegerSigned();
		if (0==trim) {
			charDictionary.setNull(idx);
			return idx;
		}
		if (trim>0) {
			trim--;//subtract for optional
		}
		
		int utfLength = reader.readIntegerUnsigned();
		if (trim>=0) {
			//append to tail
			//System.err.println("oldString :"+charDictionary.get(idx, new StringBuilder())+" TAIL");
			reader.readTextUTF8(charDictionary.rawAccess(), charDictionary.makeSpaceForAppend(idx, trim, utfLength), utfLength);
			//System.err.println("new UTF8 Opp   trim tail "+trim+" added to head "+utfLength+" string:"+charDictionary.get(idx, new StringBuilder()));
		} else {
			//append to head
			//System.err.println("oldString :"+charDictionary.get(idx, new StringBuilder())+" HEAD");
			reader.readTextUTF8(charDictionary.rawAccess(), charDictionary.makeSpaceForPrepend(idx, -trim, utfLength), utfLength);
			//System.err.println("new UTF8 Opp   trim head "+trim+" added to head "+utfLength+" string:"+charDictionary.get(idx, new StringBuilder()));
		}
		
		return idx;
	}

	public int readUTF8TailOptional(int token, int readFromIdx) {
		int idx = token & INSTANCE_MASK;
		
		int trim = reader.readIntegerUnsigned();
		if (trim==0) {
			charDictionary.setNull(idx);
			return idx;
		} 
		trim--;
		
		int utfLength = reader.readIntegerUnsigned(); //subtract for optional

		//append to tail	
		reader.readTextUTF8(charDictionary.rawAccess(), charDictionary.makeSpaceForAppend(idx, trim, utfLength), utfLength);
		
		return idx;
	}

	public int readASCII(int token, int readFromIdx) {
		int idx = token & INSTANCE_MASK;
		byte val = reader.readTextASCIIByte();
		if (0!=(val&0x7F)) {
			//real data, this is the most common case;
			charDictionary.setZeroLength(idx);				
			fastHeapAppend(idx, val);
		} else {
			readASCIIToHeapNone(idx, val);
		}
		return idx;
	}
	
	public int readTextASCIIOptional(int token, int readFromIdx) {
		return readASCII(token, readFromIdx);
	}

	public int readUTF8(int token, int readFromIdx) {
		int idx = token & INSTANCE_MASK;
		int length = reader.readIntegerUnsigned();
		reader.readTextUTF8(charDictionary.rawAccess(), 
				            charDictionary.allocate(idx, length),
				            length);
		return idx;
	}

	public int readUTF8Optional(int token, int readFromIdx) {
		int idx = token & INSTANCE_MASK;
		int length = reader.readIntegerUnsigned()-1;
		reader.readTextUTF8(charDictionary.rawAccess(), 
				            charDictionary.allocate(idx, length),
				            length);
		return idx;
	}

	public void reset(int idx) {
		if (null!=charDictionary) {
			charDictionary.setNull(idx);
		}
	}

	
}
