/*
 * This file is a part of Mossy.
 * Copyright (c) 2018, Max Roncace <mproncace@gmail.com>
 *
 * The MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package net.caseif.mossy.assembly;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import net.caseif.moslib.AddressingMode;
import net.caseif.moslib.Instruction;
import net.caseif.moslib.Mnemonic;
import net.caseif.mossy.assembly.model.ConstantFormula;
import net.caseif.mossy.assembly.model.Directive;
import net.caseif.mossy.assembly.model.NamedConstant;
import net.caseif.mossy.assembly.model.Statement;
import net.caseif.mossy.assembly.model.Token;
import net.caseif.mossy.assembly.parser.AssemblyLexer;
import net.caseif.mossy.assembly.parser.AssemblyParser;
import net.caseif.mossy.util.exception.AssemblerException;
import net.caseif.mossy.util.exception.LexerException;
import net.caseif.mossy.util.exception.ParserException;
import net.caseif.mossy.util.tuple.Pair;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ProgramAssembler {

    private List<Statement> statements;

    public void read(InputStream input) throws IOException, LexerException, ParserException {
        System.out.println("Lexing source...");

        List<List<Token>> tokenized = AssemblyLexer.lex(input);

        System.out.println("Parsing source...");

        statements = new ArrayList<>();

        for (List<Token> line : tokenized) {
            statements.addAll(AssemblyParser.parse(line));
        }
    }

    public void assemble(OutputStream output) throws IOException, AssemblerException {
        checkState(statements != null, "No program loaded.");

        System.out.println("Building constant dictionary...");

        Map<String, NamedConstant> constantDict = buildConstantDictionary();

        System.out.println("Assembling parsed source to bytecode...");

        byte[] bytecode = generateBytecode(constantDict);

        System.out.println("Writing bytecode output...");

        output.write(bytecode);

        output.close();
    }

    private byte[] generateBytecode(Map<String, NamedConstant> constantDict) throws AssemblerException {
        ByteArrayOutputStream intermediate = new ByteArrayOutputStream();

        int pc = 0;

        for (Statement stmt : statements) {
            switch (stmt.getType()) {
                case INSTRUCTION: {
                    Statement.InstructionStatement instrStmt = (Statement.InstructionStatement) stmt;

                    int operand = 0;
                    int size = 0;

                    if (instrStmt.getConstantFormula().isPresent()) {
                        Pair<Integer, Integer> resolution = instrStmt.getConstantFormula().get().resolve(constantDict);
                        operand = resolution.first();
                        size = resolution.second();
                    }

                    AddressingMode addrMode;
                    if (instrStmt.getAddressingMode().isPresent()) {
                        addrMode = instrStmt.getAddressingMode().get();
                    } else {
                        addrMode = size == 1 ? AddressingMode.ZRP : AddressingMode.ABS;
                    }

                    // this is necessary since X/Y-indexed instructions with a non-numeric opperand default to absolute
                    if (size == 1) {
                        if (addrMode == AddressingMode.ABX) {
                            // verify the zero-paged variant actually exists
                            if (Instruction.lookup(instrStmt.getMnemonic(), AddressingMode.ZPX).isPresent()) {
                                addrMode = AddressingMode.ZPX;
                            }
                        } else if (addrMode == AddressingMode.ABY) {
                            // verify the zero-paged variant actually exists
                            if (Instruction.lookup(instrStmt.getMnemonic(), AddressingMode.ZPY).isPresent()) {
                                addrMode = AddressingMode.ZPY;
                            }
                        }
                    }

                    if (addrMode == AddressingMode.REL) {
                        // the offset is relative to the address following the current instruction
                        // since the addressing mode is always relative (1 byte operand), we can
                        // just add 2 to the current offset to move it past the operand.
                        operand -= (pc + 2);
                    }

                    if (addrMode.getLength() < size) {
                        throw new AssemblerException("Operand for addressing mode " + addrMode.name()
                                + " must be at most " + addrMode.getLength() + " bytes.", instrStmt.getLine());
                    }

                    Optional<Instruction> instrOpt = Instruction.lookup(instrStmt.getMnemonic(), addrMode);

                    if (!instrOpt.isPresent()) {
                        throw new AssemblerException(String.format(
                                "Instruction %s cannot be used with addressing mode %s.",
                                instrStmt.getMnemonic(), addrMode
                        ), instrStmt.getLine());
                    }

                    intermediate.write((byte) instrOpt.get().getOpcode());

                    pc += 1;

                    if (addrMode != AddressingMode.IMP) {
                        switch (addrMode.getLength() - 1) {
                            case 1:
                                intermediate.write((byte) operand);

                                break;
                            case 2:
                                intermediate.write((byte) (operand & 0xFF)); // write LSB
                                intermediate.write((byte) ((operand >> 8) & 0xFF)); // write MSB

                                break;
                            default: // all 6502 addressing modes take either a word or a dword
                                throw new AssertionError("Unhandled case " + (addrMode.getLength() - 1));
                        }

                        pc += addrMode.getLength() - 1;
                    }

                    break;
                }
                case DIRECTIVE: {
                    Statement.DirectiveStatement dirStmt = (Statement.DirectiveStatement) stmt;

                    switch (dirStmt.getDirective()) {
                        case DB: {
                            for (ConstantFormula cf : dirStmt.getParams()) {
                                // we don't care about the size since we always write one byte
                                int val = cf.resolve(constantDict).first();

                                intermediate.write(val & 0xff);

                                pc += 1;
                            }

                            break;
                        }
                        case DW: {
                            for (ConstantFormula cf : dirStmt.getParams()) {
                                // we don't care about the size since we always write two bytes
                                int val = cf.resolve(constantDict).first();

                                intermediate.write(val & 0xff);         // write low byte
                                intermediate.write((val >> 8) & 0xff);  // write high byte

                                pc += 2;
                            }

                            break;
                        }
                        default:
                            //TODO: implement more cases
                            //throw new AssertionError("Unhandled case " + dirStmt.getDirective().name());
                    }

                    break;
                }
                // we already ingested all constant/label definitions
                case NAMED_CONSTANT_DEF:
                case LABEL_DEF:
                case COMMENT:
                    continue;
                default:
                    throw new AssertionError("Unhandled case " + stmt.getType().name());
            }
        }

        return intermediate.toByteArray();
    }

    // helper method for reading and indexing all label definitions
    private Map<String, NamedConstant> buildConstantDictionary() throws AssemblerException {
        // We have to do four passes:
        // First, we discover all defined labels so we can put them into a dictionary along with their size.
        // Second, we compute the size of all constants based on the labels we discovered (+ previously defined constants).
        // Third, we compute the actual offsets of labels based on the size dictionary we built.
        // Fourth, we compute all constants based on the partial dictionary we built.

        // first pass
        List<String> discoveredLabels = discoverLabels();

        // second pass
        Map<String, Pair<Integer, ConstantFormula>> constantSizes = computeConstantSizes(discoveredLabels);

        Map<String, Integer> mergedConstantSizes = new HashMap<>();
        discoveredLabels.forEach(lbl -> mergedConstantSizes.put(lbl, 2));
        constantSizes.forEach((name, pair) -> mergedConstantSizes.put(name, pair.first()));

        // third pass
        Map<String, NamedConstant> labelDefs = computeLabelOffsets(mergedConstantSizes);

        // fourth pass
        Map<String, NamedConstant> constantDefs = computeConstantDefs(
                constantSizes.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().second())),
                labelDefs
        );

        Map<String, NamedConstant> mergedConstantMap = new HashMap<>();
        mergedConstantMap.putAll(labelDefs);
        mergedConstantMap.putAll(constantDefs);

        return mergedConstantMap;
    }

    private List<String> discoverLabels() throws AssemblerException {
        List<String> labels = new ArrayList<>();

        for (Statement stmt : statements) {
            try {
                if (stmt.getType() == Statement.Type.LABEL_DEF) {
                    // we don't care about the actual offset right now
                    // all label references occupy 2 bytes
                    labels.add(((Statement.LabelDefinitionStatement) stmt).getName());
                }
            } catch (Throwable t) {
                throw new AssemblerException(t, stmt.getLine());
            }
        }

        return labels;
    }

    private Map<String, Pair<Integer, ConstantFormula>> computeConstantSizes(List<String> labels) throws AssemblerException {
        Map<String, Integer> sizes = new HashMap<>();
        Map<String, ConstantFormula> forms = new HashMap<>();

        for (Statement stmt : statements) {
            if (stmt.getType() != Statement.Type.NAMED_CONSTANT_DEF) {
                continue;
            }

            Statement.ConstantDefinitionStatement cdStmt = (Statement.ConstantDefinitionStatement) stmt;

            ConstantFormula cf = ((Statement.ConstantDefinitionStatement) stmt).getConstantFormula();

            forms.put(cdStmt.getName(), cdStmt.getConstantFormula());

            int maxSize = 0;

            for (int i = 0; i < cf.getValues().size(); i++) {
                Integer size;

                if (i < cf.getSizes().size() && cf.getSizes().get(i) != null) {
                    size = cf.getSizes().get(i);
                } else if (cf.getValues().get(i) instanceof String) {
                    String ref = (String) cf.getValues().get(i);

                    if (labels.contains(ref)) {
                        // label refs are always two bytes
                        size = 2;
                    } else {
                        size = sizes.get(ref);
                    }

                    if (size == null) {
                        throw new AssemblerException("Reference to undefined constant " + ref, stmt.getLine());
                    }

                    if (i < cf.getMasks().size() && cf.getMasks().get(i) != null) {
                        size = 1;
                    }
                } else {
                    throw new AssertionError("Cannot get size for constant " + cdStmt.getName() + " (part " + (i + 1)
                            + ") on line " + stmt.getLine());
                }

                assert size != null;

                if (size > maxSize) {
                    maxSize = size;
                }
            }
            sizes.put(cdStmt.getName(), maxSize);
        }

        return sizes.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> Pair.of(e.getValue(), forms.get(e.getKey()))));
    }

    private Map<String, NamedConstant> computeLabelOffsets(Map<String, Integer> constantSizes) throws AssemblerException {
        Map<String, NamedConstant> labelDict = new HashMap<>();

        int pc = 0;
        for (Statement stmt : statements) {
            try {
                if (stmt.getType() == Statement.Type.LABEL_DEF) {
                    Statement.LabelDefinitionStatement lblStmt = (Statement.LabelDefinitionStatement) stmt;

                    if (labelDict.containsKey(lblStmt.getName())) {
                        throw new AssemblerException("Found duplicate label " + lblStmt.getName() + "!", lblStmt.getLine());
                    }

                    //System.out.println(lblStmt.getName() + ": " + Integer.toHexString(pc));

                    // add the label to the dictionary - no need to increment the PC
                    labelDict.put(lblStmt.getName(), new NamedConstant(lblStmt.getName(), pc, 2));
                } else if (stmt.getType() == Statement.Type.INSTRUCTION) {
                    // we need to read instructions because they affect the PC
                    Statement.InstructionStatement instrStmt = (Statement.InstructionStatement) stmt;

                    int operandLength = computeOperandSize(constantSizes, instrStmt);

                    // just increment the program counter appropriately
                    if (instrStmt.getAddressingMode().isPresent()) {
                        int operandLengthInBytecode = instrStmt.getAddressingMode().get().getLength();

                        if (operandLength == 1) {
                            if (instrStmt.getAddressingMode().get() == AddressingMode.ABX) {
                                // verify the zero-paged variant actually exists
                                if (Instruction.lookup(instrStmt.getMnemonic(), AddressingMode.ZPX).isPresent()) {
                                    operandLengthInBytecode = 1;
                                }
                            } else if (instrStmt.getAddressingMode().get() == AddressingMode.ABY) {
                                // verify the zero-paged variant actually exists
                                if (Instruction.lookup(instrStmt.getMnemonic(), AddressingMode.ZPY).isPresent()) {
                                    operandLengthInBytecode = 1;
                                }
                            }
                        }

                        pc += operandLengthInBytecode;
                    } else {
                        checkState(instrStmt.getConstantFormula().isPresent());

                        pc += 1 + operandLength;
                    }
                } else if (stmt.getType() == Statement.Type.DIRECTIVE) {
                    Statement.DirectiveStatement dirStmt = (Statement.DirectiveStatement) stmt;
                    if (dirStmt.getDirective() == Directive.ORG) {
                        pc = getOrgOffset(dirStmt);
                    } else if (dirStmt.getDirective() == Directive.DB) {
                        pc += dirStmt.getParams().size();
                    } else if (dirStmt.getDirective() == Directive.DW) {
                        pc += dirStmt.getParams().size() * 2;
                    }
                }
            } catch (Throwable t) {
                throw new AssemblerException(t, stmt.getLine());
            }
        }

        return labelDict;
    }

    private int computeOperandSize(Map<String, Integer> constantSizes, Statement.InstructionStatement stmt) throws AssemblerException {
        if (!stmt.getConstantFormula().isPresent()) {
            return 0;
        }

        ConstantFormula cf = stmt.getConstantFormula().get();

        int maxSize = 0;

        for (int i = 0; i < cf.getValues().size(); i++) {
            Integer size;

            if (i < cf.getSizes().size() && cf.getSizes().get(i) != null) {
                size = cf.getSizes().get(i);
            } else if (cf.getValues().get(i) instanceof String) {
                String ref = (String) cf.getValues().get(i);

                size = constantSizes.get(ref);

                if (size == null) {
                    throw new AssemblerException("Reference to undefined constant " + ref, stmt.getLine());
                }
            } else {
                throw new AssertionError("Cannot get size for operand (part " + (i + 1)
                        + ") on line " + stmt.getLine());
            }

            assert size != null;

            if (i < cf.getMasks().size() && cf.getMasks().get(i) != null) {
                size = 1;
            }

            if (size > maxSize) {
                maxSize = size;
            }
        }
        return maxSize;
    }

    private int getOrgOffset(Statement.DirectiveStatement stmt) throws AssemblerException {
        int orgOffset;

        if (stmt.getParams().size() != 1) {
            throw new AssemblerException(".org directive must have exactly 1 operand (found "
                    + stmt.getParams().size() + ").", stmt.getLine());
        }

        ConstantFormula cf = stmt.getParams().get(0);

        if (cf.getValues().size() != 1) {
            throw new AssemblerException(".org directive cannot contain arithmetic.", stmt.getLine());
        }

        Object val = cf.getValues().get(0);

        if (!(val instanceof Integer)) {
            throw new AssemblerException(".org directive operand must be numeric.", stmt.getLine());
        }

        orgOffset = (int) val;
        return orgOffset;
    }

    private Map<String, NamedConstant> computeConstantDefs(Map<String, ConstantFormula> formulas, Map<String, NamedConstant> labelDefs) throws AssemblerException {
        Map<String, NamedConstant> constantMap = new HashMap<>(labelDefs);

        for (Map.Entry<String, ConstantFormula> e : formulas.entrySet()) {
            Pair<Integer, Integer> res = e.getValue().resolve(constantMap);
            NamedConstant nc = new NamedConstant(e.getKey(), res.first(), res.second());
            constantMap.put(nc.getName(), nc);
        }

        return constantMap;
    }
}
