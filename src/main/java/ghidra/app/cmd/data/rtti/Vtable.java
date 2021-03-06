package ghidra.app.cmd.data.rtti;

import ghidra.app.cmd.data.rtti.gcc.VtableModel;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;

public interface Vtable {
    
	public static final Vtable NO_VTABLE = VtableModel.NO_VTABLE;
	
	public static boolean isValid(Vtable vtable) {
		return vtable != NO_VTABLE;
	}

    /**
     * Returns the TypeInfo Model this vtable points to
     * @return the pointed to TypeInfo Model
     */
    public ClassTypeInfo getTypeInfo();

    /**
     * Gets the addresses of this vtable's function tables
     * @return the addresses of this vtable's function tables
     */
    public Address[] getTableAddresses();

    /**
     * Gets the function tables in this vtable
     * @return this vtable's function tables
     */
    public Function[][] getFunctionTables();

    /**
     * Checks if this vtable contains the specified function
     * @param function the function to check for
     * @return true if this vtable contains the specified function
     */
    public boolean containsFunction(Function function);
}