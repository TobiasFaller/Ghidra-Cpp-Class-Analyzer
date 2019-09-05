package ghidra.app.cmd.data.rtti.gcc;

import ghidra.program.model.listing.Data;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressSetView;
import ghidra.program.model.listing.Program;
import ghidra.program.model.mem.MemBuffer;
import ghidra.program.model.mem.MemoryBlock;
import ghidra.program.model.reloc.Relocation;
import ghidra.program.model.reloc.RelocationTable;
import ghidra.program.model.symbol.Namespace;
import ghidra.program.model.util.CodeUnitInsertionException;
import ghidra.util.exception.CancelledException;
import ghidra.util.task.TaskMonitor;
import ghidra.program.model.data.CategoryPath;
import ghidra.program.model.data.DataType;
import ghidra.program.model.data.DataTypePath;
import ghidra.program.model.data.InvalidDataTypeException;
import ghidra.program.model.data.TerminatedStringDataType;
import ghidra.program.util.ProgramMemoryUtil;
import ghidra.program.model.data.DataUtilities.ClearDataMode;
import ghidra.app.cmd.data.rtti.TypeInfo;
import ghidra.app.cmd.data.rtti.gcc.factory.TypeInfoFactory;
import ghidra.app.util.demangler.DemangledObject;
import ghidra.app.util.demangler.DemangledType;

import static ghidra.program.model.data.DataUtilities.createData;

import java.util.List;
import java.util.Set;
import static ghidra.app.util.datatype.microsoft.MSDataTypeUtils.getAbsoluteAddress;
import static ghidra.app.util.demangler.DemanglerUtil.demangle;

public class TypeInfoUtils {

    private TypeInfoUtils() {
    }

    private static Data createString(Program program, Address address) {
        try {
            DataType dt = new TerminatedStringDataType();
            return createData(program, address, dt, -1, false, ClearDataMode.CLEAR_ALL_CONFLICT_DATA);
        } catch (CodeUnitInsertionException e) {
            return null;
        }
    }

    /**
     * Gets the typename for the __type_info at the specified address.
     * 
     * @param Program the program to be searched.
     * @param Address the address of the TypeInfo Model's DataType.
     * @return The TypeInfo's typename string or "" if invalid.
     */
    public static String getTypeName(Program program, Address address) {
        int pointerSize = program.getDefaultPointerSize();
        Address nameAddress = getAbsoluteAddress(program, address.add(pointerSize));
        if (nameAddress == null) {
            return "";
        }
        Data data = program.getListing().getDataAt(nameAddress);
        if (data == null || !data.hasStringValue()) {
            data = createString(program, nameAddress);
        }
        if (data != null && data.hasStringValue()) {
            String result = (String) data.getValue();
            /*
             * Some anonymous namespaces typename strings start with * Unfortunately the *
             * causes issues with demangling so exclude it
             */
            return result.startsWith("*") ? result.substring(1) : result;
        }
        return "";
    }

    /**
     * Locates the TypeInfo with the specified typeString.
     * 
     * @param Program     the program to be searched.
     * @param String      the typename of the typeinfo to search for.
     * @param TaskMonitor the active task monitor.
     * @return the TypeInfo with the corresponding typename or invalid if it doesn't
     *         exist.
     * @throws InvalidDataTypeException
     */
    public static TypeInfo findTypeInfo(Program program, String typename, TaskMonitor monitor)
        throws CancelledException {
            return findTypeInfo(program, program.getAddressFactory().getAddressSet(),
                                typename, monitor);
    }

    /**
     * Locates the TypeInfo with the specified typename.
     * 
     * @param Program        the program to be searched.
     * @param AddressSetView the address set to be searched.
     * @param String         the typename to search for.
     * @param TaskMonitor    the active task monitor.
     * @return the TypeInfo with the corresponding typename or null if it doesn't
     *         exist.
     */
    public static TypeInfo findTypeInfo(Program program, AddressSetView set, String typename,
        TaskMonitor monitor) throws CancelledException {
            int pointerAlignment =
                program.getDataTypeManager().getDataOrganization().getDefaultPointerAlignment();
            List<Address> stringAddress = findTypeString(program, set, typename, monitor);
            if (stringAddress.isEmpty() || stringAddress.size() > 1) {
                return null;
            }
            Set<Address> references = ProgramMemoryUtil.findDirectReferences(program,
                pointerAlignment, stringAddress.get(0), monitor);
            if (references.isEmpty()) {
                return null;
            }
            for (Address reference : references) {
                Address typeinfoAddress = reference.subtract(program.getDefaultPointerSize());
                TypeInfo typeinfo = TypeInfoFactory.getTypeInfo(program, typeinfoAddress);
                if (typeinfo == null) {
                    continue;
                }
                try {
                    if (typeinfo.getTypeName().equals(typename)) {
                        return typeinfo;
                    }
                } catch (InvalidDataTypeException e) {
                    continue;
                }
            } return null;
    }

    private static List<Address> findTypeString(Program program, AddressSetView set,
        String typename, TaskMonitor monitor) throws CancelledException {
            List<MemoryBlock> dataBlocks = GnuUtils.getAllDataBlocks(program);
            List<Address> typeInfoAddresses =
                ProgramMemoryUtil.findString(typename, program, dataBlocks, set, monitor);
            return typeInfoAddresses;
    }

    /**
     * Gets the identifier string for the __type_info at the specified address.
     * @param Program the program to be searched.
     * @param Address the address of the TypeInfo Model's DataType.
     * @return The TypeInfo's identifier string or "" if invalid.
     */
    public static String getIDString(Program program, Address address) {
        RelocationTable table = program.getRelocationTable();
        if (table.isRelocatable()) {
            Relocation reloc = table.getRelocation(address);
            if (reloc != null) {
                String baseTypeName = reloc.getSymbolName();
                if (baseTypeName != null) {
                    return baseTypeName.substring(4);
                }
            }
        }
        final int POINTER_SIZE = program.getDefaultPointerSize();
        Address baseVtableAddress = getAbsoluteAddress(program, address);
        if (baseVtableAddress == null || baseVtableAddress.getOffset() == 0) {
            return "";
        }
        Address baseTypeInfoAddress = getAbsoluteAddress(
            program, baseVtableAddress.subtract(POINTER_SIZE));
        if (baseTypeInfoAddress == null) {
            return "";
        }
        return TypeInfoUtils.getTypeName(program, baseTypeInfoAddress);
    }

    /**
     * Checks if a typeinfo* is located at the specified address.
     * 
     * @param Program the program to be searched.
     * @param Address the address of the suspected pointer
     * @return true if a typeinfo* is present at the address.
     */
    public static boolean isTypeInfoPointer(Program program, Address address) {
        Address pointee = getAbsoluteAddress(program, address);
        if (pointee == null) {
            return false;
        }
        return isTypeInfo(program, pointee);
    }

    /**
     * Checks if a typeinfo* is present at the buffer's address.
     * 
     * @param MemBuffer
     * @return true if a typeinfo* is present at the buffer's address.
     */
    public static boolean isTypeInfoPointer(MemBuffer buf) {
        return buf != null ?
            isTypeInfoPointer(buf.getMemory().getProgram(), buf.getAddress()) : false;
    }

    /**
     * @see ghidra.app.cmd.data.rtti.gcc.factory.TypeInfoFactory#isTypeInfo
     */
    public static boolean isTypeInfo(Program program, Address address) {
        /* Makes more sense to have it in this utility, but more convient to check 
           if it is valid or not within the factory */
        return TypeInfoFactory.isTypeInfo(program, address);
    }
    
    /**
     * @see ghidra.app.cmd.data.rtti.gcc.factory.TypeInfoFactory#isTypeInfo
     */
    public static boolean isTypeInfo(MemBuffer buf) {
        return TypeInfoFactory.isTypeInfo(buf);
    }

    /**
     * Gets the Namespace for the corresponding typename.
     * @param program
     * @param typename
     * @return the Namespace for the corresponding typename.
     */
    public static Namespace getNamespaceFromTypeName(Program program, String typename) {
        return DemangledObject.createNamespace(
            program, getDemangledType(typename), program.getGlobalNamespace(), false);
    }

    public static DataType getDataType(Program program, String typename) {
        return TypeInfoFactory.getDataType(program, typename);
    }

    public static DemangledType getDemangledType(String typename) {
        DemangledObject demangled = demangle("_ZTI"+typename);
        return demangled != null ? demangled.getNamespace() : null;
    }

    /**
     * Retrieves the DataTypePath for the represented datatype.
     * 
     * @param TypeInfo
     * @return the TypeInfo represented datatype's DataTypePath.
     * @throws InvalidDataTypeException
     */
    public static DataTypePath getDataTypePath(TypeInfo type) throws InvalidDataTypeException {
        Namespace ns = type.getNamespace().getParentNamespace();
        String path;
        if (ns.isGlobal()) {
            path = "";
        } else {
            path = Namespace.NAMESPACE_DELIMITER+ns.getName(true);
        }
        path = path.replaceAll(Namespace.NAMESPACE_DELIMITER, CategoryPath.DELIMITER_STRING);
        return new DataTypePath(path, type.getName());
    }

}