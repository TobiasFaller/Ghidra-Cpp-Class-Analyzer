package ghidra.app.cmd.data.rtti;

import java.util.Set;

import ghidra.program.model.data.DataType;
import ghidra.program.model.data.Structure;
import ghidra.program.model.listing.GhidraClass;
import ghidra.util.exception.CancelledException;
import ghidra.util.task.DummyCancellableTaskMonitor;
import ghidra.util.task.TaskMonitor;

/**
 * Interface for modeling std::type_info and its derivatives.
 * <br>
 * All derived models are based on dwarf information from libstdc++.a
 */
public interface ClassTypeInfo extends TypeInfo {
    
    default DataType getRepresentedDataType() {
        return getClassDataType();
    }

    /**
     * Gets the corresponding Ghidra Class for this TypeInfo and
     * creates it if none exists.
     * @return The class representation of this TypeInfo instance
     */
    GhidraClass getGhidraClass();

    /**
     * Checks if this ClassTypeInfo is a subclass of another type
     * @return true if this is a subclass
     */
    boolean hasParent();

    /**
	 * Retrieves the Models of this ClassTypeInfo classes base classes
     * @return the parent models
     */ 
    ClassTypeInfo[] getParentModels();

    /**
     * Retrieves an ordered set of all virtually inherited base classes
     * @return the set of virtual bases
     */
    public Set<ClassTypeInfo> getVirtualParents();

    /**
     * Determines if the represented class is an abstract base
     * @return true if abstract
     */
    boolean isAbstract();

    /**
     * Gets the TypeInfo's Vtable Model
     * @param monitor the task monitor to be used while searching for the vtable
     * @return The TypeInfo's Vtable Model or {@link Vtable#NO_VTABLE} if none exists
     * @throws CancelledException if the search is cancelled
     */
    Vtable getVtable(TaskMonitor monitor) throws CancelledException;

    /**
     * Gets the TypeInfo's Vtable Model
     * @return The TypeInfo's Vtable Model or {@link Vtable#NO_VTABLE} if none exists
     */
    default Vtable getVtable() {
        try {
            return getVtable(new DummyCancellableTaskMonitor());
        }
        catch (CancelledException e) {
            return null;
        }
    }

    /**
     * Gets the underlying structure of the class for this ClassTypeInfo
     * @return the structure datatype for this class
     */
    Structure getClassDataType();

    /**
     * Gets a unique typename for this ClassTypeInfo instance
     * The resulting string should be identical across all architectures and binaries
     * for a compiler.
     * @return a unique typename string
     */
    String getUniqueTypeName();
    
}