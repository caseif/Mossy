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

import com.google.common.base.Preconditions;
import net.caseif.moslib.AddressingMode;
import net.caseif.moslib.Instruction;
import net.caseif.moslib.Mnemonic;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ProgramAssembler {

    private List<Statement> statements;

    public void read(InputStream input) throws IOException, LexerException, ParserException {
        System.out.println("Lexing assembly...");

        List<List<Token>> tokenized = AssemblyLexer.lex(input);

        System.out.println("Parsing assembly...");

        statements = new ArrayList<>();

        for (List<Token> line : tokenized) {
            statements.addAll(AssemblyParser.parse(line));
        }
    }

    public void assemble(OutputStream output) throws IOException, AssemblerException {
        checkState(statements != null, "No program loaded.");

        Map<String, NamedConstant> labelDict = buildLabelDictionary();

        byte[] bytecode = generateBytecode(labelDict);

        output.write(bytecode);

        output.close();
    }

    private byte[] generateBytecode(Map<String, NamedConstant> labelDict) throws AssemblerException {
        ByteArrayOutputStream intermediate = new ByteArrayOutputStream();

        int orgOffset = 0;

        int curOffset = 0;

        Map<String, NamedConstant> constants = new HashMap<>();

        constants.putAll(labelDict);

        for (Statement stmt : statements) {
            switch (stmt.getType()) {
                case INSTRUCTION: {
                    Statement.InstructionStatement instrStmt = (Statement.InstructionStatement) stmt;

                    int operand = 0;
                    int size = 0;

                    if (instrStmt.getConstantFormula().isPresent()) {
                        Pair<Integer, Integer> resolution = instrStmt.getConstantFormula().get().resolve(constants);
                        operand = resolution.first();
                        size = resolution.second();
                    }

                    AddressingMode addrMode;
                    if (instrStmt.getAddressingMode().isPresent()) {
                        addrMode = instrStmt.getAddressingMode().get();
                    } else {
                        addrMode = size == 1 ? AddressingMode.ZRP : AddressingMode.ABS;
                    }

                    if (size == 1) {
                        if (addrMode == AddressingMode.ABX) {
                            addrMode = AddressingMode.ZPX;
                        } else if (addrMode == AddressingMode.ABY) {
                            addrMode = AddressingMode.ZPY;
                        }
                    }

                    if (addrMode == AddressingMode.REL) {
                        // the offset is relative to the address following the current instruction
                        // since the addressing mode is always relative (1 byte operand), we can
                        // just add 2 to the current offset to move it past the operand.
                        operand -= (curOffset + 2);
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

                    curOffset += 1;

                    if (addrMode != AddressingMode.IMP) {
                        // we only need to adjust the operand to account for the offset if
                        // we're jumping to an absolute address. we should keep it intact
                        // if we're using an indirect value.

                        if (instrStmt.getMnemonic().getType() == Mnemonic.Type.JUMP
                                && addrMode == AddressingMode.ABS) {
                            operand += orgOffset;
                        }

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

                        curOffset += addrMode.getLength() - 1;
                    }

                    break;
                }
                case DIRECTIVE: {
                    Statement.DirectiveStatement dirStmt = (Statement.DirectiveStatement) stmt;

                    switch (dirStmt.getDirective()) {
                        case ORG:
                            if (!dirStmt.getParam().isPresent()) {
                                throw new AssemblerException("ORG directive requires a parameter.", dirStmt.getLine());
                            }

                            if (!(dirStmt.getParam().get() instanceof Integer)) {
                                throw new AssemblerException("ORG directive requires a number parameter.", dirStmt.getLine());
                            }

                            orgOffset = (int) dirStmt.getParam().get();

                            break;
                        default:
                            throw new AssertionError("Unhandled case " + dirStmt.getDirective().name());
                    }

                    break;
                }
                case NAMED_CONSTANT_DEF: {
                    Statement.ConstantDefinitionStatement constDefStmt = (Statement.ConstantDefinitionStatement) stmt;

                    String name = constDefStmt.getName();

                    if (constants.containsKey(name)) {
                        throw new AssemblerException(String.format("Constant %s defined multiple times.", name), stmt.getLine());
                    }

                    List<Integer> sizes = new ArrayList<>(constDefStmt.getConstantFormula().getSizes());

                    for (Object v : constDefStmt.getConstantFormula().getValues()) {
                        if (v instanceof String) {
                            sizes.add(constants.get(v).getSize());
                        }
                    }

                    int resolvedValue;
                    int resolvedSize;
                    try {
                        Pair<Integer, Integer> resolved = constDefStmt.getConstantFormula().resolve(constants);
                        resolvedValue = resolved.first();
                        resolvedSize = resolved.second();
                    } catch (AssemblerException ex) {
                        throw new AssemblerException("Failed to resolve constant " + name + ".", ex, constDefStmt.getLine());
                    }
                    NamedConstant nc = new NamedConstant(name, resolvedValue, resolvedSize);

                    constants.put(name, nc);

                    break;
                }
                // skip label definitions since we already built a dictionary
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
    private Map<String, NamedConstant> buildLabelDictionary() throws AssemblerException {
        Map<String, NamedConstant> constantDict = new HashMap<>();
        List<String> definedLabels = new ArrayList<>();

        // first pass, for discovering which labels are defined (since they can be used ahead of their definition)
        for (Statement stmt : statements) {
            if  (stmt.getType() == Statement.Type.LABEL_DEF) {
                definedLabels.add(((Statement.LabelDefinitionStatement) stmt).getName());
            }
        }

        // construct a temporary combined label dictionary which includes all labels
        Map<String, NamedConstant> combinedConstantDict = new HashMap<>(constantDict);
        for (String label : definedLabels) {
            // actual value doesn't matter, size is always 2 for label references
            combinedConstantDict.put(label, new NamedConstant(label, 0, 2));
        }

        int pc = 0;

        // second pass, for building the label dictionary
        for (Statement stmt : statements) {
            try {
                switch (stmt.getType()) {
                    case INSTRUCTION: {
                        Statement.InstructionStatement instrStmt = (Statement.InstructionStatement) stmt;

                        // just increment the program counter appropriately
                        if (instrStmt.getAddressingMode().isPresent()) {
                            pc += instrStmt.getAddressingMode().get().getLength();
                        } else {
                            checkState(instrStmt.getConstantFormula().isPresent());

                            int size = instrStmt.getConstantFormula().get().resolve(combinedConstantDict).second();

                            pc += 1 + size;
                        }

                        break;
                    }
                    case LABEL_DEF: {
                        Statement.LabelDefinitionStatement lblStmt = (Statement.LabelDefinitionStatement) stmt;

                        if (constantDict.containsKey(lblStmt.getName())) {
                            throw new AssemblerException("Found duplicate label " + lblStmt.getName() + "!", lblStmt.getLine());
                        }

                        // add the label to the dictionary - no need to increment the PC
                        constantDict.put(lblStmt.getName(), new NamedConstant(lblStmt.getName(), pc, 2));

                        break;
                    }
                    case NAMED_CONSTANT_DEF:
                    case DIRECTIVE:
                    case COMMENT: {
                        continue;
                    }
                    default: {
                        throw new AssertionError("Unhandled case " + stmt.getType().name());
                    }
                }
            } catch (Throwable t) {
                throw new AssemblerException(t, stmt.getLine());
            }
        }

        return constantDict;
    }

    private static int max(List<Integer> list) {
        checkArgument(list.size() > 0, "List must not be empty.");

        int max = Integer.MIN_VALUE;

        for (int v : list) {
            if (v > max) {
                max = v;
            }
        }

        return max;
    }

}
