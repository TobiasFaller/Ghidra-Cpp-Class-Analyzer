package ghidra.app.plugin.prototype.CppCodeAnalyzerPlugin.gcc;

import java.util.ArrayList;
import java.util.List;

import ghidra.app.cmd.data.rtti.ClassTypeInfo;
import ghidra.app.cmd.data.rtti.TypeInfo;
import ghidra.app.cmd.data.rtti.gcc.VtableModel;
import ghidra.app.cmd.data.rtti.gcc.VtableUtils;
import ghidra.app.cmd.data.rtti.gcc.VttModel;
import ghidra.app.cmd.data.rtti.gcc.factory.TypeInfoFactory;
import ghidra.app.plugin.prototype.GccRttiAnalyzer;
import ghidra.app.plugin.prototype.CppCodeAnalyzerPlugin.AbstractConstructorAnalysisCmd;
import ghidra.app.plugin.prototype.CppCodeAnalyzerPlugin.AbstractCppClassAnalyzer;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Program;
import ghidra.program.model.symbol.Symbol;
import ghidra.program.model.symbol.SymbolTable;

import static ghidra.app.cmd.data.rtti.gcc.GnuUtils.isGnuCompiler;

public class GccCppClassAnalyzer extends AbstractCppClassAnalyzer {

    private static final String NAME = "GCC C++ Class Analyzer";
    private GccVtableAnalysisCmd vtableAnalyzer;

    public GccCppClassAnalyzer() {
        super(NAME);
        setPriority(new GccRttiAnalyzer().getPriority().after());
    }

    @SuppressWarnings("hiding")
    @Override
    public boolean canAnalyze(Program program) {
        return isGnuCompiler(program);
    }

    @Override
    protected boolean hasVtt() {
        return true;
    }

    @Override
    protected List<ClassTypeInfo> getClassTypeInfoList() {
        List<ClassTypeInfo> classes = new ArrayList<>();
        SymbolTable table = program.getSymbolTable();
        for (Symbol symbol : table.getSymbols(TypeInfo.SYMBOL_NAME)) {
            TypeInfo type = TypeInfoFactory.getTypeInfo(program, symbol.getAddress());
            if (type instanceof ClassTypeInfo) {
                classes.add((ClassTypeInfo) type);
            }
        }
        return classes;
    }

    @Override
    protected AbstractConstructorAnalysisCmd getConstructorAnalyzer() {
        this.vtableAnalyzer = new GccVtableAnalysisCmd();
        return new GccConstructorAnalysisCmd();
    }

    @Override
    protected boolean isDestructor(Function function) {
        return function.getName().startsWith("~");
    }

    @Override
    protected boolean analyzeVftable(ClassTypeInfo type) {
        vtableAnalyzer.setTypeInfo(type);
        return vtableAnalyzer.applyTo(program);
    }

    @Override
    protected boolean analyzeConstructor(ClassTypeInfo type) {
		VtableModel vtable = (VtableModel) type.getVtable();
		VttModel vtt = VtableUtils.getVttModel(program, vtable);
		if (vtt.isValid()) {
			((GccConstructorAnalysisCmd) constructorAnalyzer).setVtt(vtt);
		} else {
			constructorAnalyzer.setTypeInfo(type);
		}
        return constructorAnalyzer.applyTo(program);
    }
}
