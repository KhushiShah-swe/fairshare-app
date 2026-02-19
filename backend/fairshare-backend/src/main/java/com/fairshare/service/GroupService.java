import com.fairshare.model.Expense;
import com.fairshare.repository.ExpenseRepository;
import com.fairshare.dto.ExpenseDTO;
import com.fairshare.dto.BalanceDTO;
import com.fairshare.dto.GroupDetailsDTO;

import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

@Autowired
private ExpenseRepository expenseRepo;

/**
 * Returns GroupDetailsDTO including members, expenses, and balance summary.
 */
@Transactional(readOnly = true)
public GroupDetailsDTO getGroupDetails(Long groupId) {
    // 1. Fetch group
    Group group = groupRepo.findById(groupId)
            .orElseThrow(() -> new RuntimeException("Group not found"));

    // 2. Fetch group members
    List<GroupMember> members = memberRepo.findByGroupId(groupId);

    // 3. Fetch group expenses
    List<Expense> expenses = expenseRepo.findByGroupId(groupId);

    // 4. Map expenses to ExpenseDTO (include addedBy name)
    List<ExpenseDTO> expenseDTOs = expenses.stream().map(exp -> 
        new ExpenseDTO(
            exp.getId(),
            exp.getDescription(),
            exp.getCategory(),
            exp.getAmount(),
            exp.getPaidByUser().getName(), // <-- member name
            exp.getExpenseDate(),
            exp.getNotes()
        )
    ).collect(Collectors.toList());

    // 5. Calculate balance per member
    Map<Long, Double> balanceMap = new HashMap<>();
    for (GroupMember member : members) {
        balanceMap.put(member.getUser().getId(), 0.0);
    }

    for (Expense exp : expenses) {
        double splitAmount = exp.getAmount() / exp.getParticipants().size();
        for (User participant : exp.getParticipants()) {
            balanceMap.put(participant.getId(), balanceMap.get(participant.getId()) - splitAmount);
        }
        // The payer gets positive amount
        Long payerId = exp.getPaidByUser().getId();
        balanceMap.put(payerId, balanceMap.get(payerId) + exp.getAmount());
    }

    // Map to BalanceDTO
    List<BalanceDTO> balances = members.stream()
            .map(member -> new BalanceDTO(member.getUser().getName(), balanceMap.get(member.getUser().getId())))
            .collect(Collectors.toList());

    // 6. Return GroupDetailsDTO
    return new GroupDetailsDTO(
            group.getId(),
            group.getName(),
            group.getType(),
            balances,
            expenseDTOs
    );
}
