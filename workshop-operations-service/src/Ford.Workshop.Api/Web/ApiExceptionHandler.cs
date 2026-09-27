using Ford.Workshop.Api.Application;
using Ford.Workshop.Api.Domain;
using Microsoft.AspNetCore.Diagnostics;
using Microsoft.AspNetCore.Mvc;

namespace Ford.Workshop.Api.Web;

public sealed class ApiExceptionHandler : IExceptionHandler
{
    public async ValueTask<bool> TryHandleAsync(HttpContext context, Exception exception, CancellationToken cancellationToken)
    {
        var (status, title) = exception switch
        {
            WorkOrderNotFoundException => (StatusCodes.Status404NotFound, "Work order not found"),
            DomainException => (StatusCodes.Status422UnprocessableEntity, "Business rule violation"),
            _ => (StatusCodes.Status500InternalServerError, "Unexpected error")
        };
        var problem = new ProblemDetails
        {
            Status = status,
            Title = title,
            Detail = status == 500 ? "An unexpected error occurred" : exception.Message,
            Type = $"https://ford.example/problems/{status}",
            Instance = context.Request.Path
        };
        context.Response.StatusCode = status;
        await context.Response.WriteAsJsonAsync(problem, cancellationToken);
        return true;
    }
}

