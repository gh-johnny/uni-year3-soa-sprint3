using Ford.Workshop.Api.Application;
using Ford.Workshop.Api.Domain;
using Microsoft.EntityFrameworkCore;

namespace Ford.Workshop.Api.Infrastructure;

public sealed class PostgresWorkOrderRepository(WorkshopDbContext context) : IWorkOrderRepository
{
    public async Task AddAsync(WorkOrder workOrder, CancellationToken cancellationToken) =>
        await context.WorkOrders.AddAsync(workOrder, cancellationToken);

    public Task<WorkOrder?> FindAsync(Guid id, CancellationToken cancellationToken) =>
        context.WorkOrders.SingleOrDefaultAsync(item => item.Id == id, cancellationToken);

    public async Task SaveChangesAsync(CancellationToken cancellationToken) =>
        await context.SaveChangesAsync(cancellationToken);
}

