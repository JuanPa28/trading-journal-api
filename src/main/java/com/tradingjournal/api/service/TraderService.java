package com.tradingjournal.api.service;

import com.tradingjournal.api.dto.TraderPatchRequest;
import com.tradingjournal.api.dto.TraderRequest;
import com.tradingjournal.api.dto.TraderResponse;
import com.tradingjournal.api.exception.ResourceNotFoundException;
import com.tradingjournal.api.model.Trader;
import com.tradingjournal.api.repository.TraderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class TraderService {

    private final TraderRepository traderRepository;

    public TraderService(TraderRepository traderRepository) {
        this.traderRepository = traderRepository;
    }

    public TraderResponse create(TraderRequest request) {
        Trader trader = new Trader();
        applyRequest(trader, request);
        return toResponse(traderRepository.save(trader));
    }

    @Transactional(readOnly = true)
    public List<TraderResponse> findAll() {
        return traderRepository.findAll().stream().map(TraderService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TraderResponse findById(Long id) {
        return toResponse(getOrThrow(id));
    }

    public TraderResponse update(Long id, TraderRequest request) {
        Trader trader = getOrThrow(id);
        applyRequest(trader, request);
        return toResponse(trader);
    }

    public TraderResponse patch(Long id, TraderPatchRequest request) {
        Trader trader = getOrThrow(id);
        if (request.fullName() != null) trader.setFullName(request.fullName());
        if (request.username() != null) trader.setUsername(request.username());
        if (request.email() != null) trader.setEmail(request.email());
        if (request.availableFunds() != null) trader.setAvailableFunds(request.availableFunds());
        return toResponse(trader);
    }

    public void delete(Long id) {
        traderRepository.delete(getOrThrow(id));
    }

    private Trader getOrThrow(Long id) {
        return traderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Trader not found: " + id));
    }

    private void applyRequest(Trader trader, TraderRequest request) {
        trader.setFullName(request.fullName());
        trader.setUsername(request.username());
        trader.setEmail(request.email());
        trader.setAvailableFunds(request.availableFunds());
    }

    static TraderResponse toResponse(Trader trader) {
        return new TraderResponse(
                trader.getId(),
                trader.getFullName(),
                trader.getUsername(),
                trader.getEmail(),
                trader.getAvailableFunds(),
                trader.getCreatedAt()
        );
    }
}
