// Particles Background Animation
console.log("✅ particles-background.js loaded");

class ParticlesBackground {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext('2d');
    this.particles = [];
    this.connections = [];
    this.mouse = { x: 0, y: 0 };
    this.animationId = null;
    
    this.config = {
      particleCount: 140,
      maxDistance: 120,
      particleSize: 2.5,
      particleSpeed: 1.75,
      particleColor: '#ffffff',
      connectionColor: '#006600',
      connectionOpacity: 0.9,
      particleOpacity: 0.9
    };
    
    this.init();
  }

  init() {
    this.resizeCanvas();
    this.createParticles();
    this.bindEvents();
    this.animate();
  }

  resizeCanvas() {
    this.canvas.width = window.innerWidth;
    this.canvas.height = window.innerHeight;
  }

  createParticles() {
    this.particles = [];
    for (let i = 0; i < this.config.particleCount; i++) {
      this.particles.push({
        x: Math.random() * this.canvas.width,
        y: Math.random() * this.canvas.height,
        vx: (Math.random() - 0.5) * this.config.particleSpeed,
        vy: (Math.random() - 0.5) * this.config.particleSpeed,
        size: Math.random() * this.config.particleSize,
        opacity: this.config.particleOpacity
      });
    }
  }

  bindEvents() {
    window.addEventListener('resize', () => {
      this.resizeCanvas();
      this.createParticles();
    });

    this.canvas.addEventListener('mousemove', (e) => {
      this.mouse.x = e.clientX;
      this.mouse.y = e.clientY;
    });

    this.canvas.addEventListener('mouseleave', () => {
      this.mouse.x = -1000;
      this.mouse.y = -1000;
    });
  }

  animate() {
    this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
    
    this.updateParticles();
    this.drawConnections();
    this.drawParticles();
    
    this.animationId = requestAnimationFrame(() => this.animate());
  }

  updateParticles() {
    this.particles.forEach(particle => {
      // Update position
      particle.x += particle.vx;
      particle.y += particle.vy;
      
      // Bounce off walls
      if (particle.x < 0 || particle.x > this.canvas.width) {
        particle.vx *= -1;
      }
      if (particle.y < 0 || particle.y > this.canvas.height) {
        particle.vy *= -1;
      }
      
      // Keep particles within bounds
      particle.x = Math.max(0, Math.min(this.canvas.width, particle.x));
      particle.y = Math.max(0, Math.min(this.canvas.height, particle.y));
    });
  }

  drawParticles() {
    this.particles.forEach(particle => {
      this.ctx.beginPath();
      this.ctx.arc(particle.x, particle.y, particle.size, 0, Math.PI * 2);
      this.ctx.fillStyle = `rgba(255, 255, 255, ${particle.opacity})`;
      this.ctx.fill();
    });
  }

  drawConnections() {
    this.particles.forEach((particle, i) => {
      // Connect to other particles
      for (let j = i + 1; j < this.particles.length; j++) {
        const otherParticle = this.particles[j];
        const distance = Math.sqrt(
          Math.pow(particle.x - otherParticle.x, 2) + 
          Math.pow(particle.y - otherParticle.y, 2)
        );
        
        if (distance < this.config.maxDistance) {
          const opacity = (1 - distance / this.config.maxDistance) * this.config.connectionOpacity;
          
          this.ctx.beginPath();
          this.ctx.moveTo(particle.x, particle.y);
          this.ctx.lineTo(otherParticle.x, otherParticle.y);
          this.ctx.strokeStyle = `rgba(0, 102, 0, ${opacity})`;
          this.ctx.lineWidth = 2;
          this.ctx.stroke();
        }
      }
      
      // Connect to mouse
      const mouseDistance = Math.sqrt(
        Math.pow(particle.x - this.mouse.x, 2) + 
        Math.pow(particle.y - this.mouse.y, 2)
      );
      
      if (mouseDistance < this.config.maxDistance) {
        const opacity = (1 - mouseDistance / this.config.maxDistance) * 0.85;
        
        this.ctx.beginPath();
        this.ctx.moveTo(particle.x, particle.y);
        this.ctx.lineTo(this.mouse.x, this.mouse.y);
        this.ctx.strokeStyle = `rgba(0, 102, 0, ${opacity})`;
        this.ctx.lineWidth = 2;
        this.ctx.stroke();
      }
    });
  }

  destroy() {
    if (this.animationId) {
      cancelAnimationFrame(this.animationId);
    }
    window.removeEventListener('resize', this.resizeCanvas);
    this.canvas.removeEventListener('mousemove', this.handleMouseMove);
    this.canvas.removeEventListener('mouseleave', this.handleMouseLeave);
  }
}

// Export for use in other modules
window.ParticlesBackground = ParticlesBackground;
